package com.shopapp.product.viewmodel.product;

import com.shopapp.product.model.enumeration.DimensionUnit;
import com.shopapp.product.viewmodel.ImageVm;
import com.shopapp.product.viewmodel.category.CategoryGetVm;
import java.util.List;
import lombok.Builder;

/**
 * Back office product detail for the edit form (YAS shape). {@code categories} are view models (id, name, slug,
 * parentId), not the JPA entities YAS serialises.
 */
@Builder
public record ProductDetailVm(
    long id,
    String name,
    String shortDescription,
    String description,
    String specification,
    String sku,
    String gtin,
    String slug,
    Boolean isAllowedToOrder,
    Boolean isPublished,
    Boolean isFeatured,
    Boolean isVisible,
    Boolean stockTrackingEnabled,
    Long stockQuantity,
    Double weight,
    DimensionUnit dimensionUnit,
    Double length,
    Double width,
    Double height,
    Double price,
    Long brandId,
    List<CategoryGetVm> categories,
    String metaTitle,
    String metaKeyword,
    String metaDescription,
    ImageVm thumbnailMedia,
    List<ImageVm> productImageMedias,
    Long taxClassId,
    Long parentId) {
}
