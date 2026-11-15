package com.shopapp.inventory.controller;

import com.shopapp.commonlibrary.constants.PageableConstant;
import com.shopapp.inventory.model.Warehouse;
import com.shopapp.inventory.model.enumeration.FilterExistInWhSelection;
import com.shopapp.inventory.service.WarehouseService;
import com.shopapp.inventory.utils.Constants;
import com.shopapp.inventory.viewmodel.product.ProductInfoVm;
import com.shopapp.inventory.viewmodel.warehouse.WarehouseDetailVm;
import com.shopapp.inventory.viewmodel.warehouse.WarehouseGetVm;
import com.shopapp.inventory.viewmodel.warehouse.WarehouseListGetVm;
import com.shopapp.inventory.viewmodel.warehouse.WarehousePostVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/backoffice/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping("/{warehouseId}/products")
    public ResponseEntity<List<ProductInfoVm>> getProductByWarehouse(
        @PathVariable Long warehouseId,
        @RequestParam(value = "productName", defaultValue = "", required = false) String productName,
        @RequestParam(value = "productSku", defaultValue = "", required = false) String productSku,
        @RequestParam(value = "existStatus", defaultValue = "ALL", required = false)
        FilterExistInWhSelection existStatus) {
        return ResponseEntity.ok(warehouseService.getProductWarehouse(warehouseId, productName, productSku,
            existStatus));
    }

    @GetMapping("/paging")
    public ResponseEntity<WarehouseListGetVm> getPageableWarehouses(
        @RequestParam(value = "pageNo", defaultValue = PageableConstant.DEFAULT_PAGE_NUMBER, required = false)
        @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = PageableConstant.DEFAULT_PAGE_SIZE, required = false)
        @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(warehouseService.getPageableWarehouses(pageNo, pageSize));
    }

    @GetMapping
    public ResponseEntity<List<WarehouseGetVm>> listWarehouses() {
        return ResponseEntity.ok(warehouseService.findAllWarehouses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WarehouseDetailVm> getWarehouse(@PathVariable("id") Long id) {
        return ResponseEntity.ok(warehouseService.findById(id));
    }

    @PostMapping
    public ResponseEntity<WarehouseGetVm> createWarehouse(@Valid @RequestBody WarehousePostVm warehousePostVm) {
        Warehouse warehouse = warehouseService.create(warehousePostVm);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(warehouse.getId()).toUri())
            .body(WarehouseGetVm.fromModel(warehouse));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateWarehouse(@PathVariable Long id,
                                                @Valid @RequestBody WarehousePostVm warehousePostVm) {
        warehouseService.update(warehousePostVm, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWarehouse(@PathVariable Long id) {
        warehouseService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
