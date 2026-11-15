package com.shopapp.product.viewmodel.product;

import com.shopapp.product.viewmodel.productattribute.ProductAttributeGroupGetVm;
import java.util.List;

public record ProductDetailGetVm(
    long id,
    String name,
    String brandName,
    List<String> productCategories,
    List<ProductAttributeGroupGetVm> productAttributeGroups,
    String shortDescription,
    String description,
    String specification,
    Boolean isAllowedToOrder,
    Boolean isPublished,
    Boolean isFeatured,
    Boolean hasOptions,
    Double price,
    String thumbnailMediaUrl,
    List<String> productImageMediaUrls,
    boolean stockTrackingEnabled,
    boolean stockAvailable,
    Long stockQuantity
) {

    /** This detail with the given image URLs (resolved after the database transaction, decisions.md 3b-19). */
    public ProductDetailGetVm withImageUrls(String thumbnailUrl, List<String> imageUrls) {
        return new ProductDetailGetVm(id, name, brandName, productCategories, productAttributeGroups, shortDescription,
            description, specification, isAllowedToOrder, isPublished, isFeatured, hasOptions, price, thumbnailUrl,
            imageUrls, stockTrackingEnabled, stockAvailable, stockQuantity);
    }
}
