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

/** Back office product create body (YAS shape; YAS's {@code productOptionValueDisplays} is not used). */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record ProductPostVm(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String slug,
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
    @NotNull @PositiveOrZero Double price,
    Boolean isAllowedToOrder,
    Boolean isPublished,
    Boolean isFeatured,
    Boolean isVisibleIndividually,
    Boolean stockTrackingEnabled,
    @Size(max = 255) String metaTitle,
    @Size(max = 255) String metaKeyword,
    @Size(max = 255) String metaDescription,
    Long thumbnailMediaId,
    List<@NotNull Long> productImageIds,
    List<@Valid @NotNull ProductVariationPostVm> variations,
    List<@Valid @NotNull ProductOptionValuePostVm> productOptionValues,
    List<@NotNull Long> relatedProductIds,
    Long taxClassId) implements ProductSaveVm<ProductVariationPostVm> {
}
