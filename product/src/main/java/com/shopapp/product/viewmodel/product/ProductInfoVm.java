package com.shopapp.product.viewmodel.product;

import com.shopapp.product.model.Product;

/**
 * Product lookup for the inventory service (YAS shape plus {@code stockTrackingEnabled} and the sellable
 * {@code stockQuantity}, shown next to the warehouse quantities — decisions.md 3c-60).
 */
public record ProductInfoVm(Long id, String name, String sku, boolean stockTrackingEnabled, Long stockQuantity) {

    public static ProductInfoVm fromProduct(Product product) {
        return new ProductInfoVm(product.getId(), product.getName(), product.getSku(),
            product.isStockTrackingEnabled(), product.getStockQuantity());
    }
}
