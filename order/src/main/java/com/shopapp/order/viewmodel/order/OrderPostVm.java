package com.shopapp.order.viewmodel.order;

import com.shopapp.order.model.enumeration.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Placing an order. Unlike YAS's OrderPostVm there are no prices, totals, tax or discount here: the server builds
 * the order from the stored checkout (decisions.md 2c-1). The shipping address is either one of the caller's saved
 * addresses ({@code shippingAddressId}, a location address id) or a new one ({@code shippingAddressPostVm}),
 * exactly one of them (3c-41). Unknown JSON fields (e.g. a client "totalPrice") are ignored.
 */
public record OrderPostVm(
    @NotBlank String checkoutId,
    Long shippingAddressId,
    @Valid OrderAddressPostVm shippingAddressPostVm,
    @Valid OrderAddressPostVm billingAddressPostVm,
    @Size(max = 255) String note,
    @NotNull PaymentMethod paymentMethod
) {
}
