package com.shopapp.product.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.product.exception.NotEnoughStockException;
import com.shopapp.product.model.Product;
import com.shopapp.product.model.StockReservation;
import com.shopapp.product.model.enumeration.FilterExistInWhSelection;
import com.shopapp.product.repository.ProductRepository;
import com.shopapp.product.repository.StockAdjustmentRepository;
import com.shopapp.product.repository.StockReservationRepository;
import com.shopapp.product.utils.Constants;
import com.shopapp.product.viewmodel.product.ProductInfoVm;
import com.shopapp.product.viewmodel.product.ProductQuantityDeltaVm;
import com.shopapp.product.viewmodel.product.ProductQuantityPostVm;
import com.shopapp.product.viewmodel.product.ProductQuantityPutVm;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product stock quantity (YAS update-quantity / subtract-quantity, plus restore-quantity for order compensation).
 * The inventory service adds or removes units (deltas, decisions.md 3c-60); orders take units atomically and may give
 * back what they took. The quantity is only ever changed by atomic UPDATE statements (3c-61).
 */
@Service
@RequiredArgsConstructor
public class ProductStockService {

    /** Stands in for an empty id list in IN / NOT IN (product ids are positive). */
    private static final List<Long> NO_IDS = List.of(-1L);

    private final ProductRepository productRepository;
    private final StockReservationRepository stockReservationRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;

    /**
     * Sets each product's stock quantity (admin set), all or nothing; an unknown product is a 404, a repeated one a
     * 400. Written with an UPDATE statement, never through the entity (decisions.md 3c-61). Inventory no longer calls
     * this: it sends deltas to {@link #adjustStockQuantity(List, String)}.
     */
    @Transactional
    public void updateProductQuantity(List<ProductQuantityPostVm> items) {
        Set<Long> ids = new HashSet<>();
        for (ProductQuantityPostVm item : items) {
            if (!ids.add(item.productId())) {
                throw new BadRequestException(Constants.ErrorCode.DUPLICATED_PRODUCT_IN_LIST, item.productId());
            }
        }
        loadAll(ids);
        items.forEach(item -> productRepository.setStock(item.productId(), item.stockQuantity()));
    }

    /**
     * Inventory's stock adjustment (decisions.md 3c-60): adds each delta to the product's current sellable stock,
     * atomically and never below 0, so units sold by orders since the last adjustment stay sold. All or nothing;
     * an unknown product is a 404, a repeated one a 400. Applied once per {@code reference}: a retried call (same
     * reference) changes nothing and succeeds; a refused call does not use up its reference.
     */
    @Transactional
    public void adjustStockQuantity(List<ProductQuantityDeltaVm> items, String reference) {
        Map<Long, Long> deltas = new TreeMap<>();
        for (ProductQuantityDeltaVm item : items) {
            if (deltas.put(item.productId(), item.delta()) != null) {
                throw new BadRequestException(Constants.ErrorCode.DUPLICATED_PRODUCT_IN_LIST, item.productId());
            }
        }
        if (stockAdjustmentRepository.insertIfAbsent(reference) == 0) {
            return;
        }
        loadAll(deltas.keySet());
        // product id order: no deadlock between two multi-item calls
        deltas.forEach(productRepository::adjustStock);
    }

    /**
     * Takes the units in one transaction, in product id order (no deadlock between two multi-item calls). Repeated
     * products are merged. Products without stock tracking are left alone. When one tracked product has too few
     * units, nothing is taken ({@link NotEnoughStockException}). The units taken are recorded under the (required)
     * {@code reference} for {@link #restoreStockQuantity(String)}; a reference already used, also by a concurrent
     * call, is a 400 {@code STOCK_RESERVATION_EXISTED}.
     */
    @Transactional
    public void subtractStockQuantity(List<ProductQuantityPutVm> items, String reference) {
        if (stockReservationRepository.existsByReference(reference)) {
            throw new BadRequestException(Constants.ErrorCode.STOCK_RESERVATION_EXISTED, reference);
        }
        Map<Long, Long> quantities = new TreeMap<>();
        items.forEach(item -> quantities.merge(item.productId(), item.quantity(), Math::addExact));
        Map<Long, Product> products = loadAll(quantities.keySet());
        String owner = AuthenticationUtils.extractUserId();
        try {
            quantities.forEach((productId, quantity) -> {
                if (!products.get(productId).isStockTrackingEnabled()) {
                    return;
                }
                if (productRepository.subtractStock(productId, quantity) == 0) {
                    throw new NotEnoughStockException(productId);
                }
                stockReservationRepository.save(new StockReservation(reference, productId, quantity, owner));
            });
        } catch (DataIntegrityViolationException ex) {
            // a concurrent call with the same reference committed first (unique reference + product)
            throw new BadRequestException(Constants.ErrorCode.STOCK_RESERVATION_EXISTED, reference);
        }
    }

    /**
     * Gives back the units the caller took under {@code reference}, once: the reservation rows are deleted in the
     * same transaction. Another caller's reference, an unknown one or one already restored changes nothing.
     */
    @Transactional
    public void restoreStockQuantity(String reference) {
        List<StockReservation> reservations = stockReservationRepository
            .findByReferenceAndOwnerOrderByProductIdAsc(reference, AuthenticationUtils.extractUserId());
        reservations.forEach(reservation ->
            productRepository.addStock(reservation.getProductId(), reservation.getQuantity()));
        stockReservationRepository.deleteAll(reservations);
    }

    /**
     * Internal (order service, realm role SERVICE; decisions.md 5-5): gives back the units taken under
     * {@code reference} whoever took them, once, like {@link #restoreStockQuantity(String)}; the order's admin or the
     * retry job can give back what the customer's token took.
     */
    @Transactional
    public void restoreStockQuantityOfAnyCaller(String reference) {
        List<StockReservation> reservations = stockReservationRepository.findByReferenceOrderByProductIdAsc(reference);
        reservations.forEach(reservation ->
            productRepository.addStock(reservation.getProductId(), reservation.getQuantity()));
        stockReservationRepository.deleteAll(reservations);
    }

    @Transactional(readOnly = true)
    public List<ProductInfoVm> getProductsForWarehouse(String name, String sku, List<Long> productIds,
                                                       FilterExistInWhSelection selection) {
        List<Long> ids = productIds == null || productIds.isEmpty() ? NO_IDS : productIds;
        return productRepository.findForWarehouse(name.trim().toLowerCase(Locale.ROOT),
                sku.trim().toLowerCase(Locale.ROOT), ids, selection.name())
            .stream().map(ProductInfoVm::fromProduct).toList();
    }

    private Map<Long, Product> loadAll(Collection<Long> ids) {
        Map<Long, Product> products = new TreeMap<>();
        productRepository.findAllById(ids).forEach(product -> products.put(product.getId(), product));
        ids.stream().filter(id -> !products.containsKey(id)).findFirst().ifPresent(id -> {
            throw new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, id);
        });
        return products;
    }
}
