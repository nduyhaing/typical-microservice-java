package com.shopapp.inventory.viewmodel.product;

/** Product {@code PUT /backoffice/products/update-quantity} item (YAS): the total over all warehouses. */
public record ProductQuantityPostVm(Long productId, Long stockQuantity) {
}
