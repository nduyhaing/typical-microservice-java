package com.shopapp.inventory.viewmodel.stock;

import com.shopapp.inventory.model.Stock;
import com.shopapp.inventory.viewmodel.product.ProductInfoVm;

public record StockVm(Long id, Long productId, String productName, String productSku, Long quantity,
                      Long reservedQuantity, Long warehouseId) {

    public static StockVm fromModel(Stock stock, ProductInfoVm product) {
        return new StockVm(stock.getId(), stock.getProductId(), product.name(), product.sku(), stock.getQuantity(),
            stock.getReservedQuantity(), stock.getWarehouse().getId());
    }
}
