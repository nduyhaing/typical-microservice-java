package com.shopapp.inventory.repository;

import com.shopapp.inventory.model.Stock;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {

    @Query("SELECT s.productId FROM Stock s WHERE s.warehouse.id = :warehouseId ORDER BY s.id")
    List<Long> getProductIdsInWarehouse(@Param("warehouseId") Long warehouseId);

    List<Stock> findByWarehouseIdOrderByIdAsc(Long warehouseId);

    boolean existsByWarehouseIdAndProductId(Long warehouseId, Long productId);

    boolean existsByWarehouseIdAndQuantityGreaterThan(Long warehouseId, long quantity);

    /** Product ids of the given stock rows, without loading (and caching) the rows themselves. */
    @Query("SELECT DISTINCT s.productId FROM Stock s WHERE s.id IN :ids")
    List<Long> findProductIdsByIdIn(@Param("ids") Collection<Long> ids);

    /**
     * Every stock row of the given products, locked in id order: concurrent adjustments of the same product queue
     * here, so each one sees the others' quantities when it computes the product's total (decisions.md 3c-25).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.productId IN :productIds ORDER BY s.id")
    List<Stock> lockByProductIdIn(@Param("productIds") Collection<Long> productIds);

    @Query("SELECT COALESCE(SUM(s.quantity), 0) FROM Stock s WHERE s.productId = :productId")
    long sumQuantityByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("DELETE FROM Stock s WHERE s.warehouse.id = :warehouseId")
    void deleteByWarehouseId(@Param("warehouseId") Long warehouseId);
}
