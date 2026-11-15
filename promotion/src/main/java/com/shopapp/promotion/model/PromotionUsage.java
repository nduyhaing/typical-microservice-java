package com.shopapp.promotion.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One use of a promotion by one customer (one order), identified by the caller's {@code reference}; a released use
 * no longer counts (YAS: one row per product, never released).
 */
@Entity
@Table(name = "promotion_usage")
@Getter
@Setter
@NoArgsConstructor
public class PromotionUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long promotionId;

    @Column(nullable = false, unique = true)
    private UUID reference;

    @Column(nullable = false)
    private String userId;

    private Long orderId;

    /** The first discounted product (YAS column). */
    private Long productId;

    private Instant releasedOn;

    @Column(insertable = false, updatable = false)
    private Instant createdOn;
}
