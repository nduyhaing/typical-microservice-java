package com.shopapp.inventory.viewmodel.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A product as the product service describes it ({@code /backoffice/products/{id}} and {@code /for-warehouse});
 * {@code existInWh} is filled in by inventory for {@code GET /backoffice/warehouses/{id}/products} (YAS).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductInfoVm(long id, String name, String sku, Boolean stockTrackingEnabled, Boolean existInWh) {

    public ProductInfoVm withExistInWh(boolean value) {
        return new ProductInfoVm(id, name, sku, stockTrackingEnabled, value);
    }
}
