package com.shopapp.order.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.order.model.Order;
import com.shopapp.order.model.enumeration.DeliveryMethod;
import com.shopapp.order.model.enumeration.DeliveryStatus;
import com.shopapp.order.model.enumeration.OrderStatus;
import com.shopapp.order.model.enumeration.PaymentStatus;
import com.shopapp.order.repository.OrderItemRepository;
import com.shopapp.order.repository.OrderRepository;
import com.shopapp.order.repository.OrderSpecifications;
import com.shopapp.order.utils.Constants;
import com.shopapp.order.viewmodel.internal.OrderPaymentInfoVm;
import com.shopapp.order.viewmodel.internal.OrderPaymentPutVm;
import com.shopapp.order.viewmodel.order.OrderBriefVm;
import com.shopapp.order.viewmodel.order.OrderDetailVm;
import com.shopapp.order.viewmodel.order.OrderListVm;
import com.shopapp.order.viewmodel.order.OrderStatusPutVm;
import com.shopapp.order.viewmodel.order.OrderVm;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What happens to an order after it is placed (decisions.md 5-1..5-6): the admin list and detail, status changes
 * through the one status machine ({@link OrderTransitions}), the customer's cancel, and the payment service marking
 * an order paid. Every status change locks the order row, so an admin, the customer and payment act one after
 * another; giving the stock and coupon use back happens after the commit ({@link OrderGoodsBackService}).
 */
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdOn"), Sort.Order.desc("id"));

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderGoodsBackService goodsBackService;
    private final TransactionTemplate transactionTemplate;

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public OrderListVm getOrders(ZonedDateTime createdFrom, ZonedDateTime createdTo, List<OrderStatus> statuses,
                                 String email, String productName, int pageNo, int pageSize) {
        Page<Order> page = orderRepository.findAll(
            OrderSpecifications.matching(createdFrom, createdTo, statuses, email, productName),
            PageRequest.of(pageNo, pageSize, NEWEST_FIRST));
        return new OrderListVm(page.getContent().stream().map(OrderBriefVm::fromModel).toList(),
            page.getTotalElements(), page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrderDetailVm getOrder(Long id) {
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.ORDER_ID_NOT_FOUND, id));
        return OrderDetailVm.fromModel(order, orderItemRepository.findAllByOrderId(id));
    }

    /**
     * Admin status change. A change the status machine does not allow is a 400 and changes nothing; the current status
     * again changes only the delivery fields (and, for an order that ended, retries giving its goods back).
     */
    public OrderDetailVm updateStatus(Long id, OrderStatusPutVm request) {
        transactionTemplate.executeWithoutResult(tx -> {
            Order order = lockedOrder(id);
            OrderStatus from = order.getOrderStatus();
            if (from == request.orderStatus()) {
                if (!OrderTransitions.isEnded(from)) {
                    applyDelivery(order, request.deliveryStatus(), request.deliveryMethod());
                }
                return;
            }
            if (!OrderTransitions.isAllowed(from, request.orderStatus())) {
                throw new BadRequestException(Constants.ErrorCode.ORDER_STATUS_NOT_ALLOWED, id, from,
                    request.orderStatus());
            }
            moveTo(order, request.orderStatus(), request.rejectReason());
            applyDelivery(order, request.deliveryStatus(), request.deliveryMethod());
        });
        goodsBackService.giveBack(id);
        return transactionTemplate.execute(tx -> getOrder(id));
    }

    // ---------------------------------------------------------------- customer

    /**
     * The customer cancels their own order while it is PENDING or PENDING_PAYMENT. Someone else's order is a 404 (its
     * existence is not revealed); an order already cancelled answers it again and retries giving its goods back, so
     * the call is safe to repeat; any other status is a 400.
     */
    public OrderVm cancelOwnOrder(Long id) {
        String customerId = AuthenticationUtils.extractUserId();
        transactionTemplate.executeWithoutResult(tx -> {
            Order order = orderRepository.findWithLockById(id)
                .filter(found -> Objects.equals(found.getCustomerId(), customerId))
                .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.ORDER_ID_NOT_FOUND, id));
            if (order.getOrderStatus() == OrderStatus.CANCELLED) {
                return;
            }
            if (!OrderTransitions.customerMayCancel(order.getOrderStatus())) {
                throw new BadRequestException(Constants.ErrorCode.ORDER_NOT_CANCELLABLE, id, order.getOrderStatus());
            }
            moveTo(order, OrderStatus.CANCELLED, null);
        });
        goodsBackService.giveBack(id);
        return transactionTemplate.execute(tx -> OrderVm.fromModel(orderRepository.findById(id).orElseThrow(),
            orderItemRepository.findAllByOrderId(id)));
    }

    // ---------------------------------------------------------------- payment service (internal)

    @Transactional(readOnly = true)
    public OrderPaymentInfoVm getPaymentInfo(Long id) {
        return OrderPaymentInfoVm.fromModel(orderRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.ORDER_ID_NOT_FOUND, id)));
    }

    /**
     * Records a completed payment (decisions.md 5-6). The amount must equal the order total; an order that ended is
     * not payable; an order already paid answers the same payment again (idempotent) and refuses another one. An
     * unpaid order (PENDING, PENDING_PAYMENT, ACCEPTED) becomes PAID; a cash-on-delivery order paid on delivery
     * (SHIPPING, COMPLETED) keeps its status and records the payment.
     */
    public OrderPaymentInfoVm markPaid(Long id, OrderPaymentPutVm payment) {
        return transactionTemplate.execute(tx -> {
            Order order = lockedOrder(id);
            if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
                if (Objects.equals(order.getPaymentId(), payment.paymentId())) {
                    return OrderPaymentInfoVm.fromModel(order);
                }
                throw new BadRequestException(Constants.ErrorCode.ORDER_ALREADY_PAID, id);
            }
            if (OrderTransitions.isEnded(order.getOrderStatus())) {
                throw new BadRequestException(Constants.ErrorCode.ORDER_NOT_PAYABLE, id, order.getOrderStatus());
            }
            if (order.getTotalPrice() == null || order.getTotalPrice().compareTo(payment.amount()) != 0) {
                throw new BadRequestException(Constants.ErrorCode.PAYMENT_AMOUNT_MISMATCH, payment.amount(), id);
            }
            if (OrderTransitions.isAllowed(order.getOrderStatus(), OrderStatus.PAID)) {
                order.setOrderStatus(OrderStatus.PAID);
            }
            order.setPaymentStatus(PaymentStatus.COMPLETED);
            order.setPaymentId(payment.paymentId());
            return OrderPaymentInfoVm.fromModel(orderRepository.saveAndFlush(order));
        });
    }

    // ---------------------------------------------------------------- helpers

    private Order lockedOrder(Long id) {
        return orderRepository.findWithLockById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.ORDER_ID_NOT_FOUND, id));
    }

    /** An allowed change: status, the payment and delivery status that follow from it, and the goods-back request. */
    private static void moveTo(Order order, OrderStatus to, String rejectReason) {
        final OrderStatus from = order.getOrderStatus();
        order.setOrderStatus(to);
        switch (to) {
            case PAID -> order.setPaymentStatus(PaymentStatus.COMPLETED);
            case SHIPPING -> order.setDeliveryStatus(DeliveryStatus.DELIVERING);
            case COMPLETED -> order.setDeliveryStatus(DeliveryStatus.DELIVERED);
            case CANCELLED, REJECT -> {
                order.setDeliveryStatus(DeliveryStatus.CANCELLED);
                if (order.getPaymentStatus() != PaymentStatus.COMPLETED) {
                    order.setPaymentStatus(PaymentStatus.CANCELLED);
                }
            }
            default -> {
                // ACCEPTED, PENDING_PAYMENT, REFUND: no derived field
            }
        }
        if (to == OrderStatus.REJECT && rejectReason != null && !rejectReason.isBlank()) {
            order.setRejectReason(rejectReason.strip());
        }
        if (OrderTransitions.givesGoodsBack(from, to)) {
            order.setGoodsBackRequestedOn(ZonedDateTime.now());
        }
    }

    private static void applyDelivery(Order order, DeliveryStatus deliveryStatus, DeliveryMethod deliveryMethod) {
        if (deliveryStatus != null) {
            order.setDeliveryStatus(deliveryStatus);
        }
        if (deliveryMethod != null) {
            order.setDeliveryMethod(deliveryMethod);
        }
    }
}
