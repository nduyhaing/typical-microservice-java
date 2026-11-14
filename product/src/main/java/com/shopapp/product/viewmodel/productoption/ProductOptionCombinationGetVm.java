package com.shopapp.product.viewmodel.productoption;

import com.shopapp.product.model.ProductOptionCombination;

public record ProductOptionCombinationGetVm(
    Long id,
    Long productOptionId,
    String productOptionValue,
    String productOptionName
) {

    public static ProductOptionCombinationGetVm fromModel(ProductOptionCombination combination) {
        return new ProductOptionCombinationGetVm(combination.getId(), combination.getProductOption().getId(),
            combination.getValue(), combination.getProductOption().getName());
    }
}
