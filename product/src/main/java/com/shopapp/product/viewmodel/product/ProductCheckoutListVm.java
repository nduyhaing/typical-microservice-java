package com.shopapp.product.viewmodel.product;

import com.shopapp.product.model.Product;
import java.time.ZonedDateTime;

/** A product as the order service sees it at checkout (YAS shape). */
public record ProductCheckoutListVm(
    Long id,
    String name,
    String description,
    String shortDescription,
    String sku,
    Long parentId,
    Long brandId,
    Double price,
    Long taxClassId,
    String thumbnailUrl,
    ZonedDateTime createdOn,
    String createdBy,
    ZonedDateTime lastModifiedOn,
    String lastModifiedBy
) {

    /** Null-safe for products without a parent or brand (YAS threw a NullPointerException without a brand). */
    public static ProductCheckoutListVm fromModel(Product product, String thumbnailUrl) {
        return new ProductCheckoutListVm(
            product.getId(),
            product.getName(),
            product.getDescription(),
            product.getShortDescription(),
            product.getSku(),
            product.getParent() == null ? null : product.getParent().getId(),
            product.getBrand() == null ? null : product.getBrand().getId(),
            product.getPrice(),
            product.getTaxClassId(),
            thumbnailUrl,
            product.getCreatedOn(),
            product.getCreatedBy(),
            product.getLastModifiedOn(),
            product.getLastModifiedBy());
    }
}
