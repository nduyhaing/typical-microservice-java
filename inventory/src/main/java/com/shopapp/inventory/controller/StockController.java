package com.shopapp.inventory.controller;

import com.shopapp.inventory.service.StockService;
import com.shopapp.inventory.utils.Constants;
import com.shopapp.inventory.viewmodel.stock.StockPostVm;
import com.shopapp.inventory.viewmodel.stock.StockQuantityUpdateVm;
import com.shopapp.inventory.viewmodel.stock.StockVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/backoffice/stocks")
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    @PostMapping
    public ResponseEntity<Void> addProductIntoWarehouse(
        @Valid @RequestBody @NotEmpty @Size(max = Constants.MAX_ITEMS) List<StockPostVm> postVms) {
        stockService.addProductIntoWarehouse(postVms);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<StockVm>> getStocksByWarehouseIdAndProductNameAndSku(
        @RequestParam(name = "warehouseId") Long warehouseId,
        @RequestParam(name = "productName", defaultValue = "", required = false) String productName,
        @RequestParam(name = "productSku", defaultValue = "", required = false) String productSku) {
        return ResponseEntity.ok(stockService.getStocksByWarehouseIdAndProductNameAndSku(warehouseId, productName,
            productSku));
    }

    /** YAS answers 200 with an empty body. */
    @PutMapping
    public ResponseEntity<Void> updateProductQuantityInStock(@Valid @RequestBody StockQuantityUpdateVm requestBody) {
        stockService.updateProductQuantityInStock(requestBody);
        return ResponseEntity.ok().build();
    }
}
