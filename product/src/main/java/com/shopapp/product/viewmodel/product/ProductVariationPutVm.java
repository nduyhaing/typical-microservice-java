package com.shopapp.product.viewmodel.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

/** A variation in a product update body (YAS shape): {@code id} set = existing variation, null = new one. */
public record ProductVariationPutVm(
    Long id,
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 255) String slug,
    @Size(max = 255) String sku,
    @Size(max = 255) String gtin,
    @NotNull @PositiveOrZero Double price,
    Long thumbnailMediaId,
    List<Long> productImageIds,
    Map<Long, String> optionValuesByOptionId
) implements ProductVariationSaveVm {
}
