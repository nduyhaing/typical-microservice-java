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
import lombok.Setter;

/** How many units of a product a warehouse holds (one row per product and warehouse). */
@Entity
@Table(name = "stock")
@Getter
@Setter
@NoArgsConstructor
public class Stock extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private long quantity;

    @Column(nullable = false)
    private long reservedQuantity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    public Stock(Long productId, Warehouse warehouse) {
        this.productId = productId;
        this.warehouse = warehouse;
    }
}
