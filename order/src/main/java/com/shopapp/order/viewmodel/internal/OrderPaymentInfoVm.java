package com.shopapp.order.viewmodel.internal;

import com.shopapp.order.model.Order;
import com.shopapp.order.model.enumeration.OrderStatus;
import com.shopapp.order.model.enumeration.PaymentStatus;
import java.math.BigDecimal;

/**
 * Internal (payment service): what payment needs to know about an order (decisions.md 5-6): its owner
 * ({@code customerId} = the customer's JWT {@code sub}, so payment can refuse someone else's order), the amount to
 * charge and its payment state.
 */
public record OrderPaymentInfoVm(
    Long id,
    String checkoutId,
    String customerId,
    String email,
    BigDecimal totalPrice,
    OrderStatus orderStatus,
    PaymentStatus paymentStatus,
    String paymentMethod,
    Long paymentId
) {

    public static OrderPaymentInfoVm fromModel(Order order) {
        return new OrderPaymentInfoVm(order.getId(), order.getCheckoutId(), order.getCustomerId(), order.getEmail(),
            order.getTotalPrice(), order.getOrderStatus(), order.getPaymentStatus(), order.getPaymentMethodId(),
            order.getPaymentId());
    }
}
