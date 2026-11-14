package com.shopapp.product.viewmodel.product;

import com.shopapp.product.model.Product;

/** Response of the back office product create (YAS shape). */
public record ProductGetDetailVm(long id, String name, String slug) {

    public static ProductGetDetailVm fromModel(Product product) {
        return new ProductGetDetailVm(product.getId(), product.getName(), product.getSlug());
    }
}
