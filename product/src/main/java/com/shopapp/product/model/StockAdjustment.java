package com.shopapp.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The reference of an inventory stock adjustment that {@code adjust-quantity} has applied, so a retried call with the
 * same reference changes nothing (decisions.md 3c-60). Rows are written by
 * {@link com.shopapp.product.repository.StockAdjustmentRepository#insertIfAbsent(String)}.
 */
@Entity
@Table(name = "stock_adjustment")
@Getter
@NoArgsConstructor
public class StockAdjustment {

    @Id
    @Column(length = 64)
    private String reference;

    @Column(nullable = false)
    private ZonedDateTime createdOn;
}
