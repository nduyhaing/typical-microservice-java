package com.shopapp.promotion.model;

import com.shopapp.commonlibrary.model.AbstractAuditEntity;
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

/** One target of a promotion: exactly one of product, category or brand id is set (matching its applyTo). */
@Entity
@Table(name = "promotion_apply")
@Getter
@Setter
@NoArgsConstructor
public class PromotionApply extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    private Long productId;

    private Long categoryId;

    private Long brandId;
}
