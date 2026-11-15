package com.shopapp.payment.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.payment.exception.PaymentException;
import com.shopapp.payment.model.Payment;
import com.shopapp.payment.model.enumeration.PaymentMethod;
import com.shopapp.payment.model.enumeration.PaymentStatus;
import com.shopapp.payment.repository.PaymentRepository;
import com.shopapp.payment.repository.PaymentSpecifications;
import com.shopapp.payment.utils.Constants;
import com.shopapp.payment.viewmodel.external.OrderPaymentInfoVm;
import com.shopapp.payment.viewmodel.external.PaypalCaptureVm;
import com.shopapp.payment.viewmodel.external.PaypalOrderVm;
import com.shopapp.payment.viewmodel.payment.CapturePaymentResponseVm;
import com.shopapp.payment.viewmodel.payment.InitPaymentRequestVm;
import com.shopapp.payment.viewmodel.payment.InitPaymentResponseVm;
import com.shopapp.payment.viewmodel.payment.PaymentCancelVm;
import com.shopapp.payment.viewmodel.payment.PaymentListVm;
import com.shopapp.payment.viewmodel.payment.PaymentVm;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Payments (YAS {@code PaymentService}, reworked for Review Focus 1, decisions.md 5-12..5-15):
 * <ul>
 *   <li>the amount always comes from the order service, never from the browser; the caller must own the order;</li>
 *   <li>COD: {@code /init} records a PENDING payment; an admin confirms it on delivery, which marks the order
 *       paid;</li>
 *   <li>PayPal: {@code /init} creates a PayPal order through payment-paypal and answers the approval URL;
 *       {@code /capture} captures it server-side, verifies amount, currency and references against the payment and
 *       the order ({@link CaptureVerifier}) and only then marks the order paid;</li>
 *   <li>one init, capture or confirmation at a time per <em>order</em> (transaction-scoped advisory lock on the
 *       order id, 5-60) plus the payment row lock; other pending attempts are cancelled by one conditional UPDATE
 *       (only rows still PENDING), every JPA update is version-checked; a replay answers the recorded result and
 *       never captures or pays twice; the PayPal order id and capture id are unique;</li>
 *   <li>before capturing, order marks the order "payment in progress" so it cannot be cancelled meanwhile (5-62);
 *       a completed payment order has not accepted yet is retried by {@link PaymentReconciler} (5-61).</li>
 * </ul>
 */
@Slf4j
@Service
public class PaymentService {

    private static final Set<String> ENDED_ORDER_STATUSES = Set.of("CANCELLED", "REJECT", "REFUND");
    private static final String COMPLETED = "COMPLETED";
    /** Longest wait between two tries of telling order about a completed payment (5-61). */
    private static final Duration MAX_CONFIRM_DELAY = Duration.ofHours(1);

    private final PaymentRepository paymentRepository;
    private final PaymentProviderService providerService;
    private final OrderClient orderClient;
    private final PaypalClient paypalClient;
    private final TransactionTemplate transactionTemplate;

    public PaymentService(PaymentRepository paymentRepository, PaymentProviderService providerService,
                          OrderClient orderClient, PaypalClient paypalClient,
                          PlatformTransactionManager transactionManager) {
        this.paymentRepository = paymentRepository;
        this.providerService = providerService;
        this.orderClient = orderClient;
        this.paypalClient = paypalClient;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // ---------------------------------------------------------------- customer

    /** Starts paying one of the caller's unpaid orders with the chosen method (amount from the order service). */
    public InitPaymentResponseVm initPayment(InitPaymentRequestVm request) {
        String customerId = AuthenticationUtils.extractUserId();
        providerService.checkEnabled(request.paymentMethod());
        OrderPaymentInfoVm order = ownOrder(request.orderId(), customerId);
        checkPayable(order);
        if (request.paymentMethod() == PaymentMethod.COD) {
            return initCod(order, customerId);
        }
        return initPaypal(order, customerId);
    }

    private InitPaymentResponseVm initCod(OrderPaymentInfoVm order, String customerId) {
        Payment payment = transactionTemplate.execute(tx -> {
            lockOrder(order.id());
            refuseWhenPaid(order.id());
            // a second "pay on delivery" for the same order (double click, back button) answers the first one
            Optional<Payment> pending = paymentRepository
                .findByOrderIdAndPaymentStatus(order.id(), PaymentStatus.PENDING).stream()
                .filter(existing -> existing.getPaymentMethod() == PaymentMethod.COD
                    && existing.getAmount().compareTo(order.totalPrice()) == 0)
                .findFirst();
            Payment cod = pending.orElseGet(() -> paymentRepository.saveAndFlush(newPayment(order, customerId,
                PaymentMethod.COD)));
            cancelOtherPending(order.id(), cod.getId());
            return cod;
        });
        return new InitPaymentResponseVm(PaymentStatus.PENDING.name(), payment.getId(), null);
    }

    private InitPaymentResponseVm initPaypal(OrderPaymentInfoVm order, String customerId) {
        Payment payment = transactionTemplate.execute(tx -> {
            lockOrder(order.id());
            refuseWhenPaid(order.id());
            Payment attempt = newPayment(order, customerId, PaymentMethod.PAYPAL);
            attempt.setPaypalRequestId(UUID.randomUUID());
            return paymentRepository.saveAndFlush(attempt);
        });
        PaypalOrderVm paypalOrder;
        try {
            paypalOrder = paypalClient.createOrder(payment.getAmount(), payment.reference(),
                payment.getPaypalRequestId());
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(tx -> paymentRepository.findById(payment.getId())
                .filter(stored -> stored.getPaymentStatus() == PaymentStatus.PENDING)
                .ifPresent(stored -> {
                    stored.setPaymentStatus(PaymentStatus.CANCELLED);
                    stored.setFailureMessage(truncate("PayPal order not created: " + ex.getMessage()));
                    paymentRepository.save(stored);
                }));
            throw ex;
        }
        transactionTemplate.executeWithoutResult(tx -> {
            lockOrder(order.id());
            refuseWhenPaid(order.id());
            Payment stored = paymentRepository.findById(payment.getId()).orElseThrow();
            if (stored.getPaymentStatus() != PaymentStatus.PENDING) {
                // another attempt (or a capture) for this order won while PayPal created this one (5-60)
                throw new BadRequestException(Constants.ErrorCode.PAYMENT_REPLACED, stored.getId());
            }
            stored.setPaymentProviderCheckoutId(paypalOrder.paypalOrderId());
            paymentRepository.saveAndFlush(stored);
            cancelOtherPending(order.id(), stored.getId());
        });
        return new InitPaymentResponseVm(paypalOrder.status(), payment.getId(), paypalOrder.approvalUrl());
    }

    /**
     * Captures the caller's approved PayPal payment ({@code token} = PayPal order id from the return URL), verifies
     * it and marks the order paid. A replay of a completed capture answers it again (and retries telling order).
     */
    public CapturePaymentResponseVm capturePayment(String token) {
        Payment found = ownPaypalPayment(token, AuthenticationUtils.extractUserId());
        CaptureOutcome outcome = transactionTemplate.execute(tx -> captureLocked(found.getOrderId(), found.getId(),
            token));
        if (outcome.problem() != null) {
            throw new BadRequestException(Constants.ErrorCode.PAYMENT_VERIFICATION_FAILED,
                outcome.payment().getOrderId(), outcome.problem());
        }
        return CapturePaymentResponseVm.fromModel(confirmWithOrder(outcome.payment()));
    }

    /** The result of the locked capture step: the payment, and why it did not pass verification (if so). */
    private record CaptureOutcome(Payment payment, String problem) {
    }

    private CaptureOutcome captureLocked(Long orderId, Long paymentId, String token) {
        lockOrder(orderId);
        Payment payment = paymentRepository.findWithLockById(paymentId)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PAYMENT_NOT_FOUND, token));
        if (payment.getPaymentStatus() == PaymentStatus.COMPLETED) {
            return new CaptureOutcome(payment, null);
        }
        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new BadRequestException(Constants.ErrorCode.PAYMENT_NOT_CAPTURABLE, payment.getId(),
                payment.getPaymentStatus());
        }
        refuseWhenPaid(orderId);
        // never take the buyer's money for an order that ended or was paid otherwise meanwhile; order blocks
        // cancelling the order while the capture runs (5-62)
        OrderPaymentInfoVm order = orderClient.markPaymentInProgress(orderId, paymentId);
        checkPayable(order);
        PaypalCaptureVm capture = paypalClient.capture(token);
        Optional<String> problem = CaptureVerifier.problem(capture, payment, order.totalPrice());
        payment.setGatewayTransactionId(capture.captureId());
        payment.setPaymentFee(capture.paymentFee());
        if (problem.isPresent()) {
            // money may have moved at PayPal: kept with its capture id for a manual refund (decisions.md 5-13)
            log.error("PayPal capture {} of payment {} (order {}) failed verification: {}", capture.captureId(),
                payment.getId(), payment.getOrderId(), problem.get());
            payment.setPaymentStatus(PaymentStatus.CANCELLED);
            payment.setFailureMessage(truncate("Capture not accepted: " + problem.get()));
            return new CaptureOutcome(paymentRepository.saveAndFlush(payment), problem.get());
        }
        payment.setPaymentStatus(PaymentStatus.COMPLETED);
        payment.setFailureMessage(null);
        return new CaptureOutcome(paymentRepository.saveAndFlush(payment), null);
    }

    /** The buyer cancelled at PayPal: their pending PayPal payment is cancelled (YAS only answered a text). */
    public PaymentCancelVm cancelPayment(String token) {
        Payment found = ownPaypalPayment(token, AuthenticationUtils.extractUserId());
        Payment payment = transactionTemplate.execute(tx -> {
            Payment locked = paymentRepository.findWithLockById(found.getId()).orElseThrow();
            if (locked.getPaymentStatus() == PaymentStatus.PENDING) {
                locked.setPaymentStatus(PaymentStatus.CANCELLED);
                locked.setFailureMessage("Cancelled by the buyer at PayPal");
                return paymentRepository.saveAndFlush(locked);
            }
            return locked;
        });
        return new PaymentCancelVm(payment.getId(), payment.getOrderId(), payment.getPaymentStatus());
    }

    // ---------------------------------------------------------------- admin

    /**
     * An admin confirms a cash-on-delivery payment (collected on delivery): order is told the payment with the
     * recorded amount (= the order total) and only then is the payment COMPLETED. Idempotent.
     */
    public PaymentVm confirmCod(Long paymentId) {
        Long orderId = paymentRepository.findById(paymentId).map(Payment::getOrderId)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PAYMENT_NOT_FOUND, paymentId));
        Payment confirmed = transactionTemplate.execute(tx -> {
            lockOrder(orderId);
            Payment payment = paymentRepository.findWithLockById(paymentId)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PAYMENT_NOT_FOUND, paymentId));
            if (payment.getPaymentMethod() != PaymentMethod.COD
                || payment.getPaymentStatus() == PaymentStatus.CANCELLED) {
                throw new BadRequestException(Constants.ErrorCode.PAYMENT_NOT_CONFIRMABLE, paymentId,
                    payment.getPaymentMethod() + " " + payment.getPaymentStatus());
            }
            if (payment.getPaymentStatus() == PaymentStatus.COMPLETED && payment.isOrderConfirmed()) {
                return payment;
            }
            if (payment.getPaymentStatus() != PaymentStatus.COMPLETED
                && paymentRepository.existsByOrderIdAndPaymentStatus(orderId, PaymentStatus.COMPLETED)) {
                // paid by another payment (a PayPal capture) meanwhile
                throw new BadRequestException(Constants.ErrorCode.ORDER_ALREADY_PAID, orderId);
            }
            orderClient.markPaid(payment.getOrderId(), payment.getId(), payment.getAmount());
            payment.setPaymentStatus(PaymentStatus.COMPLETED);
            payment.setOrderConfirmed(true);
            payment.setFailureMessage(null);
            return paymentRepository.saveAndFlush(payment);
        });
        return PaymentVm.fromModel(confirmed);
    }

    public PaymentListVm getPayments(Long orderId, PaymentStatus status, PaymentMethod method, boolean needsAttention,
                                     int pageNo, int pageSize) {
        Page<Payment> page = paymentRepository.findAll(PaymentSpecifications.filtered(orderId, status, method,
                needsAttention),
            PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc("createdOn"), Sort.Order.desc("id"))));
        List<PaymentVm> payments = page.getContent().stream().map(PaymentVm::fromModel).toList();
        return new PaymentListVm(payments, page.getNumber(), page.getSize(), page.getTotalElements(),
            page.getTotalPages(), page.isLast());
    }

    // ---------------------------------------------------------------- helpers

    /** The order, when it exists and belongs to the caller; another user's order reads as not found. */
    private OrderPaymentInfoVm ownOrder(Long orderId, String customerId) {
        OrderPaymentInfoVm order = orderClient.getOrder(orderId);
        if (!Objects.equals(order.customerId(), customerId)) {
            throw new NotFoundException(Constants.ErrorCode.ORDER_NOT_FOUND, orderId);
        }
        return order;
    }

    /** The caller's PayPal payment for this PayPal order id; someone else's reads as not found. */
    private Payment ownPaypalPayment(String token, String customerId) {
        return paymentRepository.findByPaymentProviderCheckoutId(token)
            .filter(payment -> payment.getPaymentMethod() == PaymentMethod.PAYPAL)
            .filter(payment -> Objects.equals(payment.getCustomerId(), customerId))
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PAYMENT_NOT_FOUND, token));
    }

    private static void checkPayable(OrderPaymentInfoVm order) {
        if (COMPLETED.equals(order.paymentStatus())) {
            throw new BadRequestException(Constants.ErrorCode.ORDER_ALREADY_PAID, order.id());
        }
        if (ENDED_ORDER_STATUSES.contains(order.orderStatus()) || order.totalPrice() == null
            || order.totalPrice().signum() <= 0) {
            throw new BadRequestException(Constants.ErrorCode.ORDER_NOT_PAYABLE, order.id(), order.orderStatus());
        }
    }

    private static Payment newPayment(OrderPaymentInfoVm order, String customerId, PaymentMethod method) {
        return Payment.builder()
            .orderId(order.id())
            .checkoutId(order.checkoutId())
            .customerId(customerId)
            .amount(order.totalPrice())
            .paymentMethod(method)
            .paymentStatus(PaymentStatus.PENDING)
            .build();
    }

    /**
     * Only the newest attempt of an order stays pending (an abandoned PayPal approval can no longer be captured):
     * one conditional UPDATE, so a payment completed meanwhile is never overwritten (5-60).
     */
    private void cancelOtherPending(Long orderId, Long keepId) {
        paymentRepository.cancelOtherPending(orderId, keepId, "Replaced by payment " + keepId);
    }

    /**
     * Serialises init, capture and confirmation per order for the rest of the transaction (5-60): a
     * transaction-scoped Postgres advisory lock on the order id (works before any payment row exists).
     */
    private void lockOrder(Long orderId) {
        paymentRepository.lockOrder(orderId);
    }

    /** Under the order lock: an order with a completed payment takes no other payment. */
    private void refuseWhenPaid(Long orderId) {
        if (paymentRepository.existsByOrderIdAndPaymentStatus(orderId, PaymentStatus.COMPLETED)) {
            throw new BadRequestException(Constants.ErrorCode.ORDER_ALREADY_PAID, orderId);
        }
    }

    /**
     * Tells order about a completed payment that order has not accepted yet. Order down: 503, the payment stays
     * COMPLETED with {@code orderConfirmed=false}; a replayed capture or {@link PaymentReconciler} tries again (with a
     * back-off). Order refusing (e.g. cancelled, or paid by another payment meanwhile): 400, recorded on the payment
     * ({@code orderRefused}) for a manual refund and no longer retried (5-61).
     */
    private Payment confirmWithOrder(Payment payment) {
        if (payment.isOrderConfirmed()) {
            return payment;
        }
        PaymentException failure = tellOrder(payment);
        if (failure != null) {
            if (failure.getStatus() == HttpStatus.BAD_REQUEST) {
                throw failure;
            }
            throw PaymentException.unavailable(Constants.ErrorCode.PAYMENT_NOT_RECORDED);
        }
        return transactionTemplate.execute(tx -> paymentRepository.findById(payment.getId()).orElseThrow());
    }

    /**
     * The reconciler's retry (5-61): tells order about one completed, unconfirmed, not refused payment.
     *
     * @return whether order accepted it now
     */
    public boolean reconcile(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null || payment.getPaymentStatus() != PaymentStatus.COMPLETED || payment.isOrderConfirmed()
            || payment.isOrderRefused()) {
            return false;
        }
        return tellOrder(payment) == null;
    }

    /** Marks the order paid and records the outcome on the payment; answers the failure, null on success. */
    private PaymentException tellOrder(Payment payment) {
        try {
            orderClient.markPaid(payment.getOrderId(), payment.getId(), payment.getAmount());
        } catch (PaymentException ex) {
            if (ex.getStatus() == HttpStatus.BAD_REQUEST) {
                log.error("Order {} refused completed payment {}: {} - refund it by hand", payment.getOrderId(),
                    payment.getId(), ex.getMessage());
                transactionTemplate.executeWithoutResult(tx -> paymentRepository.markOrderRefused(payment.getId(),
                    truncate("Order refused the payment: " + ex.getMessage())));
            } else {
                int attempts = payment.getOrderConfirmAttempts() + 1;
                transactionTemplate.executeWithoutResult(tx -> paymentRepository.recordConfirmFailure(
                    payment.getId(), ZonedDateTime.now().plus(confirmRetryDelay(attempts))));
            }
            return ex;
        }
        transactionTemplate.executeWithoutResult(tx -> paymentRepository.markOrderConfirmed(payment.getId()));
        return null;
    }

    /** Wait before the next try after {@code attempts} failed tries: 30 s, doubling, at most an hour (5-61). */
    static Duration confirmRetryDelay(int attempts) {
        int doublings = Math.max(0, Math.min(attempts - 1, 20));
        Duration delay = Duration.ofSeconds(30L << doublings);
        return delay.compareTo(MAX_CONFIRM_DELAY) > 0 ? MAX_CONFIRM_DELAY : delay;
    }

    private static String truncate(String message) {
        String text = message == null ? "" : message;
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
