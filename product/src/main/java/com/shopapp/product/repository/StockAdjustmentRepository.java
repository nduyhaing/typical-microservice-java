package com.shopapp.product.repository;

import com.shopapp.product.model.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, String> {

    /**
     * Records the reference; returns 0 when it is already recorded. A concurrent call with the same reference waits
     * for the first one's transaction: 0 when it committed, 1 when it rolled back (PostgreSQL ON CONFLICT).
     */
    @Modifying
    @Query(value = "INSERT INTO stock_adjustment (reference, created_on) VALUES (:reference, now()) "
        + "ON CONFLICT (reference) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("reference") String reference);
}
