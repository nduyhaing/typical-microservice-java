package com.shopapp.inventory.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.DuplicatedException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.inventory.model.Warehouse;
import com.shopapp.inventory.model.enumeration.FilterExistInWhSelection;
import com.shopapp.inventory.repository.StockHistoryRepository;
import com.shopapp.inventory.repository.StockRepository;
import com.shopapp.inventory.repository.WarehouseRepository;
import com.shopapp.inventory.utils.Constants.ErrorCode;
import com.shopapp.inventory.viewmodel.address.AddressVm;
import com.shopapp.inventory.viewmodel.product.ProductInfoVm;
import com.shopapp.inventory.viewmodel.warehouse.WarehouseDetailVm;
import com.shopapp.inventory.viewmodel.warehouse.WarehouseGetVm;
import com.shopapp.inventory.viewmodel.warehouse.WarehouseListGetVm;
import com.shopapp.inventory.viewmodel.warehouse.WarehousePostVm;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Warehouses (YAS WarehouseService). The address is stored in location; the remote call is made so that a failure
 * leaves nothing half-saved (decisions.md 3c-27): create saves the warehouse only after location created the address
 * (and deletes that address again when the save fails); update and delete change the database first, call location
 * while the transaction is still open, and roll back when location fails.
 */
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final ProductService productService;
    private final LocationService locationService;

    @Transactional(readOnly = true)
    public List<WarehouseGetVm> findAllWarehouses() {
        return warehouseRepository.findAll(Sort.by("name", "id")).stream().map(WarehouseGetVm::fromModel).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseListGetVm getPageableWarehouses(int pageNo, int pageSize) {
        Page<Warehouse> page = warehouseRepository.findAll(PageRequest.of(pageNo, pageSize, Sort.by("id")));
        return new WarehouseListGetVm(page.getContent().stream().map(WarehouseGetVm::fromModel).toList(),
            page.getNumber(), page.getSize(), (int) page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    /** The warehouse with its address from location (location down → 500). */
    public WarehouseDetailVm findById(Long id) {
        Warehouse warehouse = getWarehouse(id);
        return WarehouseDetailVm.of(warehouse, locationService.getAddress(warehouse.getAddressId()));
    }

    /** Not transactional: the remote call happens before anything is written. */
    public Warehouse create(WarehousePostVm vm) {
        String name = vm.name().trim();
        if (warehouseRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicatedException(ErrorCode.NAME_ALREADY_EXITED, name);
        }
        AddressVm address = locationService.createAddress(vm.toAddress());
        try {
            return warehouseRepository.saveAndFlush(new Warehouse(name, address.id()));
        } catch (RuntimeException ex) {
            locationService.deleteAddressQuietly(address.id());
            if (ex instanceof DataIntegrityViolationException) {
                throw new DuplicatedException(ErrorCode.NAME_ALREADY_EXITED, name);
            }
            throw ex;
        }
    }

    @Transactional
    public void update(WarehousePostVm vm, Long id) {
        Warehouse warehouse = getWarehouse(id);
        String name = vm.name().trim();
        if (warehouseRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicatedException(ErrorCode.NAME_ALREADY_EXITED, name);
        }
        warehouse.setName(name);
        warehouseRepository.flush();
        locationService.updateAddress(warehouse.getAddressId(), vm.toAddress());
    }

    /**
     * A warehouse that still holds units cannot be deleted (YAS deleted it and left the stock rows dangling). Empty
     * stock rows and their history go with the warehouse.
     */
    @Transactional
    public void delete(Long id) {
        final Warehouse warehouse = getWarehouse(id);
        if (stockRepository.existsByWarehouseIdAndQuantityGreaterThan(id, 0)) {
            throw new BadRequestException(ErrorCode.WAREHOUSE_HAS_STOCK, id);
        }
        stockHistoryRepository.deleteByWarehouseId(id);
        stockRepository.deleteByWarehouseId(id);
        warehouseRepository.delete(warehouse);
        warehouseRepository.flush();
        locationService.deleteAddress(warehouse.getAddressId());
    }

    /**
     * Products for the back office picker (YAS {@code /warehouses/{id}/products}): matching name/sku, in the
     * warehouse (YES), not in it (NO) or both (ALL), each marked with {@code existInWh}.
     */
    @Transactional(readOnly = true)
    public List<ProductInfoVm> getProductWarehouse(Long warehouseId, String productName, String productSku,
                                                   FilterExistInWhSelection existStatus) {
        getWarehouse(warehouseId);
        Set<Long> productIds = new HashSet<>(stockRepository.getProductIdsInWarehouse(warehouseId));
        if (existStatus == FilterExistInWhSelection.YES && productIds.isEmpty()) {
            return List.of();
        }
        return productService.filterProducts(productName, productSku, productIds, existStatus).stream()
            .map(product -> product.withExistInWh(productIds.contains(product.id())))
            .toList();
    }

    Warehouse getWarehouse(Long id) {
        return warehouseRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(ErrorCode.WAREHOUSE_NOT_FOUND, id));
    }
}
