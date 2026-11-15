package com.shopapp.product.viewmodel.product;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** One item of {@code PUT /backoffice/products/update-quantity} (YAS shape): the product's new stock quantity. */
public record ProductQuantityPostVm(@NotNull Long productId, @NotNull @Min(0) Long stockQuantity) {
}
