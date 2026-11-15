package com.shopapp.payment.viewmodel.payment;

import com.shopapp.payment.model.Payment;
import com.shopapp.payment.model.enumeration.PaymentMethod;
import com.shopapp.payment.model.enumeration.PaymentStatus;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

/**
 * A payment in the admin list (shop_app addition: YAS has no payments list; decisions.md 5-17). {@code needsAttention}
 * (5-63): money was captured at PayPal but order has not accepted it ({@code COMPLETED} and not confirmed: being
 * retried, or refused by order) or the capture failed verification (cancelled with a capture id) — refund or follow up
 * by hand.
 */
public record PaymentVm(
    Long id,
    Long orderId,
    String checkoutId,
    String customerId,
    BigDecimal amount,
    BigDecimal paymentFee,
    PaymentMethod paymentMethod,
    PaymentStatus paymentStatus,
    String gatewayTransactionId,
    String paymentProviderCheckoutId,
    String failureMessage,
    boolean orderConfirmed,
    boolean orderRefused,
    boolean needsAttention,
    ZonedDateTime createdOn,
    ZonedDateTime lastModifiedOn
) {

    public static PaymentVm fromModel(Payment payment) {
        return new PaymentVm(payment.getId(), payment.getOrderId(), payment.getCheckoutId(),
            payment.getCustomerId(), payment.getAmount(), payment.getPaymentFee(), payment.getPaymentMethod(),
            payment.getPaymentStatus(), payment.getGatewayTransactionId(), payment.getPaymentProviderCheckoutId(),
            payment.getFailureMessage(), payment.isOrderConfirmed(), payment.isOrderRefused(),
            needsAttention(payment), payment.getCreatedOn(), payment.getLastModifiedOn());
    }

    /** Captured money order did not accept (yet), or a capture that failed verification (5-63). */
    public static boolean needsAttention(Payment payment) {
        return payment.getPaymentStatus() == PaymentStatus.COMPLETED && !payment.isOrderConfirmed()
            || payment.getPaymentStatus() == PaymentStatus.CANCELLED && payment.getGatewayTransactionId() != null;
    }
}
