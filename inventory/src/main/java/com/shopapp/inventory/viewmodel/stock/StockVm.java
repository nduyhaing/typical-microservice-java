package com.shopapp.inventory.viewmodel.stock;

import com.shopapp.inventory.model.Stock;
import com.shopapp.inventory.viewmodel.product.ProductInfoVm;

/**
 * A stock row: {@code quantity} is what the warehouse physically holds, {@code productStockQuantity} the product's
 * sellable stock over all warehouses (orders take from it; decisions.md 3c-60).
 */
public record StockVm(Long id, Long productId, String productName, String productSku, Long quantity,
                      Long reservedQuantity, Long warehouseId, Long productStockQuantity) {

    public static StockVm fromModel(Stock stock, ProductInfoVm product) {
        return new StockVm(stock.getId(), stock.getProductId(), product.name(), product.sku(), stock.getQuantity(),
            stock.getReservedQuantity(), stock.getWarehouse().getId(), product.stockQuantity());
    }
}
