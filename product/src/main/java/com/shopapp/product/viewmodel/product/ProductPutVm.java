package com.shopapp.product.viewmodel.product;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.shopapp.product.model.enumeration.DimensionUnit;
import com.shopapp.product.viewmodel.productoption.ProductOptionValuePostVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Back office product update body (YAS shape). It replaces the product's fields, brand, categories, images and
 * related products; variations with an id are updated, without an id created.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record ProductPutVm(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String slug,
    @NotNull @PositiveOrZero Double price,
    Boolean isAllowedToOrder,
    Boolean isPublished,
    Boolean isFeatured,
    Boolean isVisibleIndividually,
    Boolean stockTrackingEnabled,
    Long brandId,
    List<@NotNull Long> categoryIds,
    @Size(max = 255) String shortDescription,
    String description,
    String specification,
    @Size(max = 255) String sku,
    @Size(max = 255) String gtin,
    @PositiveOrZero Double weight,
    DimensionUnit dimensionUnit,
    @PositiveOrZero Double length,
    @PositiveOrZero Double width,
    @PositiveOrZero Double height,
    @Size(max = 255) String metaTitle,
    @Size(max = 255) String metaKeyword,
    @Size(max = 255) String metaDescription,
    Long thumbnailMediaId,
    List<@NotNull Long> productImageIds,
    List<@Valid @NotNull ProductVariationPutVm> variations,
    List<@Valid @NotNull ProductOptionValuePostVm> productOptionValues,
    List<@NotNull Long> relatedProductIds,
    Long taxClassId) implements ProductSaveVm<ProductVariationPutVm> {
}
