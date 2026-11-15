package com.shopapp.product.viewmodel.product;

import com.shopapp.product.model.Product;

/** Product lookup for the inventory service (YAS shape plus {@code stockTrackingEnabled}). */
public record ProductInfoVm(Long id, String name, String sku, boolean stockTrackingEnabled) {

    public static ProductInfoVm fromProduct(Product product) {
        return new ProductInfoVm(product.getId(), product.getName(), product.getSku(),
            product.isStockTrackingEnabled());
    }
}
