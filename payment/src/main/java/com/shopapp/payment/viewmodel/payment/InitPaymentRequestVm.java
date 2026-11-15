package com.shopapp.payment.viewmodel.payment;

import com.shopapp.payment.model.enumeration.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Start paying one of the caller's orders. YAS {@code InitPaymentRequestVm} took {@code totalPrice} and
 * {@code checkoutId} from the browser; here only the order id: the amount comes from the order service
 * (decisions.md 5-12). Unknown JSON fields (e.g. a client {@code totalPrice}) are ignored.
 */
public record InitPaymentRequestVm(@NotNull @Positive Long orderId, @NotNull PaymentMethod paymentMethod) {
}
