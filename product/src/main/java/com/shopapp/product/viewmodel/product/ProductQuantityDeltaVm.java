package com.shopapp.product.viewmodel.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One item of {@code PUT /backoffice/products/adjust-quantity}: units the inventory added to (positive) or removed
 * from (negative) the product's warehouses (decisions.md 3c-60).
 */
public record ProductQuantityDeltaVm(@NotNull Long productId,
                                     @NotNull @Min(-1_000_000_000) @Max(1_000_000_000) Long delta) {
}
