package com.shopapp.inventory.controller;

import com.shopapp.inventory.service.StockHistoryService;
import com.shopapp.inventory.viewmodel.stockhistory.StockHistoryListVm;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/stocks/histories")
@RequiredArgsConstructor
public class StockHistoryController {

    private final StockHistoryService stockHistoryService;

    @GetMapping
    public ResponseEntity<StockHistoryListVm> getStockHistories(@RequestParam Long productId,
                                                                @RequestParam Long warehouseId) {
        return ResponseEntity.ok(stockHistoryService.getStockHistories(productId, warehouseId));
    }
}
