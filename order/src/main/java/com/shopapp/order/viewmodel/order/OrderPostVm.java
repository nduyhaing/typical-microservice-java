package com.shopapp.order.viewmodel.order;

import com.shopapp.order.model.enumeration.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Placing an order. Unlike YAS's OrderPostVm there are no prices, totals, tax or discount here: the server builds
 * the order from the stored checkout (decisions.md 2c-1). Unknown JSON fields (e.g. a client "totalPrice") are
 * ignored.
 */
public record OrderPostVm(
    @NotBlank String checkoutId,
    @NotNull @Valid OrderAddressPostVm shippingAddressPostVm,
    @Valid OrderAddressPostVm billingAddressPostVm,
    @Size(max = 255) String note,
    @NotNull PaymentMethod paymentMethod
) {
}
