package com.shopapp.payment.viewmodel.external;

import java.math.BigDecimal;

/**
 * Order's internal {@code GET /internal/orders/{id}} (decisions.md 5-6): owner ({@code customerId} = JWT sub), the
 * amount to charge and the order's payment state.
 */
public record OrderPaymentInfoVm(
    Long id,
    String checkoutId,
    String customerId,
    String email,
    BigDecimal totalPrice,
    String orderStatus,
    String paymentStatus,
    String paymentMethod,
    Long paymentId
) {
}
