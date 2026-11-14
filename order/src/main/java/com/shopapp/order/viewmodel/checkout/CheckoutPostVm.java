package com.shopapp.order.viewmodel.checkout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** What the browser sends to start a checkout: product ids and quantities only, never prices. */
public record CheckoutPostVm(
    @Size(max = 255) String note,
    @NotEmpty @Size(max = 100) List<@Valid CheckoutItemPostVm> checkoutItemPostVms
) {
}
