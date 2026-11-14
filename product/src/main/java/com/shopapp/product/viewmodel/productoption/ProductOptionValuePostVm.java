package com.shopapp.product.viewmodel.productoption;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * The values a product offers for one option (YAS shape, used by create and update): e.g. Color → [Black, Violet].
 */
public record ProductOptionValuePostVm(
    @NotNull Long productOptionId,
    @Size(max = 255) String displayType,
    Integer displayOrder,
    List<@NotNull @Size(max = 255) String> value) {
}
