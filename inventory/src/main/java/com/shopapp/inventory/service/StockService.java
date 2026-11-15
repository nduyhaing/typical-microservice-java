package com.shopapp.inventory.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.inventory.model.Stock;
import com.shopapp.inventory.model.StockHistory;
import com.shopapp.inventory.model.Warehouse;
import com.shopapp.inventory.model.enumeration.FilterExistInWhSelection;
import com.shopapp.inventory.repository.StockHistoryRepository;
import com.shopapp.inventory.repository.StockRepository;
import com.shopapp.inventory.utils.Constants.ErrorCode;
import com.shopapp.inventory.viewmodel.product.ProductInfoVm;
import com.shopapp.inventory.viewmodel.product.ProductQuantityDeltaVm;
import com.shopapp.inventory.viewmodel.stock.StockPostVm;
import com.shopapp.inventory.viewmodel.stock.StockQuantityUpdateVm;
import com.shopapp.inventory.viewmodel.stock.StockQuantityVm;
import com.shopapp.inventory.viewmodel.stock.StockVm;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock rows (YAS StockService). A warehouse quantity is what the warehouse physically holds; the product's stock
 * quantity is what can still be sold (orders take from it). So an adjustment sends product only its change, summed
 * per product, never the warehouse total, which would put back the units sold since (decisions.md 3c-60; YAS and
 * 3c-25 sent the total). That call is made inside the transaction, after the rows are written and before the commit:
 * when product fails, the transaction rolls back and nothing is saved (3c-27).
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final StockRepository stockRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final WarehouseService warehouseService;
    private final ProductService productService;

    /**
     * Puts products into warehouses with quantity 0. Each product must exist and have stock tracking enabled; a
     * product already in the warehouse, or the same pair twice in the list, is a 400; an unknown warehouse a 404.
     */
    @Transactional
    public void addProductIntoWarehouse(List<StockPostVm> postVms) {
        Set<String> pairs = new HashSet<>();
        Map<Long, Warehouse> warehouses = new HashMap<>();
        for (StockPostVm vm : postVms) {
            if (!pairs.add(vm.productId() + "@" + vm.warehouseId())) {
                throw new BadRequestException(ErrorCode.DUPLICATED_ITEM_IN_LIST, vm.productId());
            }
            Warehouse warehouse = warehouses.computeIfAbsent(vm.warehouseId(), warehouseService::getWarehouse);
            if (stockRepository.existsByWarehouseIdAndProductId(warehouse.getId(), vm.productId())) {
                throw new BadRequestException(ErrorCode.STOCK_ALREADY_EXISTED, vm.productId(), warehouse.getId());
            }
        }
        Set<Long> productIds = new TreeSet<>();
        postVms.forEach(vm -> productIds.add(vm.productId()));
        // one lookup for the whole list (3c review M3); a parent with options holds no stock and is not found
        Map<Long, ProductInfoVm> products = productService.getProducts(productIds).stream()
            .collect(Collectors.toMap(ProductInfoVm::id, Function.identity(), (first, second) -> first));
        for (Long productId : productIds) {
            ProductInfoVm product = products.get(productId);
            if (product == null) {
                throw new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND, productId);
            }
            if (!Boolean.TRUE.equals(product.stockTrackingEnabled())) {
                throw new BadRequestException(ErrorCode.PRODUCT_STOCK_TRACKING_DISABLED, productId);
            }
        }
        // quantity 0: the sellable stock does not change, product is not called (3c-60)
        stockRepository.saveAllAndFlush(postVms.stream()
            .map(vm -> new Stock(vm.productId(), warehouses.get(vm.warehouseId())))
            .toList());
    }

    /** The warehouse's stock rows whose product matches the name/sku search, with the product's name and sku. */
    @Transactional(readOnly = true)
    public List<StockVm> getStocksByWarehouseIdAndProductNameAndSku(Long warehouseId, String productName,
                                                                    String productSku) {
        warehouseService.getWarehouse(warehouseId);
        List<Stock> stocks = stockRepository.findByWarehouseIdOrderByIdAsc(warehouseId);
        if (stocks.isEmpty()) {
            return List.of();
        }
        Map<Long, ProductInfoVm> products = productService.filterProducts(productName, productSku,
                stocks.stream().map(Stock::getProductId).toList(), FilterExistInWhSelection.YES).stream()
            .collect(Collectors.toMap(ProductInfoVm::id, Function.identity(), (first, second) -> first));
        return stocks.stream()
            .filter(stock -> products.containsKey(stock.getProductId()))
            .map(stock -> StockVm.fromModel(stock, products.get(stock.getProductId())))
            .toList();
    }

    /**
     * Adds (or removes, when negative) units, all or nothing: an unknown stock is a 404, a stock listed twice or a
     * result below 0 a 400 (YAS checked {@code adjusted < 0 && adjusted > quantity}, which is never true). Each
     * adjustment writes a history row with its note and the call's reference. Product gets the change summed per
     * product under that reference (products whose changes cancel out are left out). Every stock row of the affected
     * products is locked first, so concurrent adjustments cannot lose an update.
     */
    @Transactional
    public void updateProductQuantityInStock(StockQuantityUpdateVm requestBody) {
        Map<Long, StockQuantityVm> adjustments = new LinkedHashMap<>();
        for (StockQuantityVm vm : requestBody.stockQuantityList()) {
            if (adjustments.put(vm.stockId(), vm) != null) {
                throw new BadRequestException(ErrorCode.DUPLICATED_ITEM_IN_LIST, vm.stockId());
            }
        }
        List<Long> productIds = stockRepository.findProductIdsByIdIn(adjustments.keySet());
        List<Stock> lockedStocks = productIds.isEmpty() ? List.of() : stockRepository.lockByProductIdIn(productIds);
        Map<Long, Stock> stocksById = lockedStocks.stream()
            .collect(Collectors.toMap(Stock::getId, Function.identity()));
        String reference = UUID.randomUUID().toString();
        List<StockHistory> histories = new ArrayList<>();
        Map<Long, Long> deltas = new TreeMap<>();
        adjustments.forEach((stockId, vm) -> {
            Stock stock = stocksById.get(stockId);
            if (stock == null) {
                throw new NotFoundException(ErrorCode.STOCK_NOT_FOUND, stockId);
            }
            long quantity = stock.getQuantity() + vm.quantity();
            if (quantity < 0) {
                throw new BadRequestException(ErrorCode.INVALID_ADJUSTED_QUANTITY, stockId, vm.quantity());
            }
            stock.setQuantity(quantity);
            histories.add(new StockHistory(stock, vm.quantity(), vm.note(), reference));
            deltas.merge(stock.getProductId(), vm.quantity(), Long::sum);
        });
        stockHistoryRepository.saveAll(histories);
        stockRepository.flush();
        List<ProductQuantityDeltaVm> changes = deltas.entrySet().stream()
            .filter(entry -> entry.getValue() != 0)
            .map(entry -> new ProductQuantityDeltaVm(entry.getKey(), entry.getValue()))
            .toList();
        if (!changes.isEmpty()) {
            productService.adjustProductQuantity(changes, reference);
        }
    }
}
