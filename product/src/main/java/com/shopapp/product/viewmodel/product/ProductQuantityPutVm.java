package com.shopapp.product.viewmodel.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One item of {@code PUT /backoffice/products/subtract-quantity} (YAS shape): how many units to take. */
public record ProductQuantityPutVm(@NotNull Long productId, @NotNull @Min(1) @Max(1_000_000_000) Long quantity) {
}
