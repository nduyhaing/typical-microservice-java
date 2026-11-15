package com.shopapp.product.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Units taken by one {@code subtract-quantity} call made with a reference, so that exactly these units can be given
 * back once by {@code restore-quantity} from the same caller (decisions.md 3c-22).
 */
@Entity
@Table(name = "stock_reservation")
@Getter
@NoArgsConstructor
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String reference;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Long quantity;

    /** JWT {@code sub} of the caller that subtracted the units. */
    @Column(nullable = false)
    private String owner;

    @Column(nullable = false)
    private ZonedDateTime createdOn;

    public StockReservation(String reference, Long productId, Long quantity, String owner) {
        this.reference = reference;
        this.productId = productId;
        this.quantity = quantity;
        this.owner = owner;
        this.createdOn = ZonedDateTime.now();
    }
}
