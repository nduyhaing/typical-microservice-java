package com.shopapp.inventory.model;

import com.shopapp.commonlibrary.model.AbstractAuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One stock adjustment: who changed a product's quantity in a warehouse, by how much, and why. */
@Entity
@Table(name = "stock_history")
@Getter
@NoArgsConstructor
public class StockHistory extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private long adjustedQuantity;

    @Column(length = 450)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    public StockHistory(Stock stock, long adjustedQuantity, String note) {
        this.productId = stock.getProductId();
        this.warehouse = stock.getWarehouse();
        this.adjustedQuantity = adjustedQuantity;
        this.note = note;
    }
}
