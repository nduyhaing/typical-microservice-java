package com.shopapp.product.viewmodel.product;

import java.util.Map;

/** A variation in a back office product body: its own name/slug/sku/gtin/price and its option values. */
public interface ProductVariationSaveVm extends ProductProperties {

    /** Option id → the value this variation has for it (e.g. Color → Black). */
    Map<Long, String> optionValuesByOptionId();
}
