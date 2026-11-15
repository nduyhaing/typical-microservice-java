package com.shopapp.inventory.service;

import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.inventory.repository.StockHistoryRepository;
import com.shopapp.inventory.viewmodel.product.ProductInfoVm;
import com.shopapp.inventory.viewmodel.stockhistory.StockHistoryListVm;
import com.shopapp.inventory.viewmodel.stockhistory.StockHistoryVm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stock adjustment history of a product in a warehouse, newest first (YAS). */
@Service
@RequiredArgsConstructor
public class StockHistoryService {

    private final StockHistoryRepository stockHistoryRepository;
    private final WarehouseService warehouseService;
    private final ProductService productService;

    /** Unknown warehouse → 404; a product unknown to the product service shows without a name. */
    @Transactional(readOnly = true)
    public StockHistoryListVm getStockHistories(Long productId, Long warehouseId) {
        warehouseService.getWarehouse(warehouseId);
        String productName = productName(productId);
        return new StockHistoryListVm(stockHistoryRepository
            .findByProductIdAndWarehouseIdOrderByCreatedOnDescIdDesc(productId, warehouseId).stream()
            .map(history -> StockHistoryVm.fromModel(history, productName))
            .toList());
    }

    private String productName(Long productId) {
        try {
            ProductInfoVm product = productService.getProduct(productId);
            return product.name();
        } catch (NotFoundException ex) {
            return null;
        }
    }
}
