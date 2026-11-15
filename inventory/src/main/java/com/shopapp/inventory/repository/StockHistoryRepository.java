package com.shopapp.inventory.repository;

import com.shopapp.inventory.model.StockHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StockHistoryRepository extends JpaRepository<StockHistory, Long> {

    List<StockHistory> findByProductIdAndWarehouseIdOrderByCreatedOnDescIdDesc(Long productId, Long warehouseId);

    @Modifying
    @Query("DELETE FROM StockHistory h WHERE h.warehouse.id = :warehouseId")
    void deleteByWarehouseId(@Param("warehouseId") Long warehouseId);
}
