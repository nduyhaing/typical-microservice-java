package com.shopapp.order.viewmodel.checkout;

import com.shopapp.order.utils.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CheckoutItemPostVm(
    @NotNull Long productId,
    @NotNull @Min(1) @Max(Constants.MAX_QUANTITY) Integer quantity
) {
}
