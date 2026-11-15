package com.shopapp.inventory.viewmodel.product;

/**
 * Product {@code PUT /backoffice/products/adjust-quantity} item: the units a stock change added to (positive) or
 * removed from (negative) the product's warehouses, summed per product (decisions.md 3c-60).
 */
public record ProductQuantityDeltaVm(Long productId, Long delta) {
}
