package com.shopapp.order.viewmodel.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Asks for the server's price of a checkout shipped to an address (same address choice and coupon as
 * {@link OrderPostVm}).
 */
public record OrderPreviewPostVm(
    @NotBlank String checkoutId,
    Long shippingAddressId,
    @Valid OrderAddressPostVm shippingAddressPostVm,
    @Size(max = 100) String couponCode
) {
}
