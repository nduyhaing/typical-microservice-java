package com.shopapp.promotion.model;

import com.shopapp.commonlibrary.model.AbstractAuditEntity;
import com.shopapp.promotion.model.enumeration.ApplyTo;
import com.shopapp.promotion.model.enumeration.DiscountType;
import com.shopapp.promotion.model.enumeration.UsageType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "promotion")
@Getter
@Setter
@NoArgsConstructor
public class Promotion extends AbstractAuditEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 450)
    private String name;

    @Column(nullable = false, length = 450)
    private String slug;

    @Column(length = 1000)
    private String description;

    /** Unique case-insensitively; matched case-insensitively. */
    @Column(nullable = false, length = 100)
    private String couponCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscountType discountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UsageType usageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApplyTo applyTo;

    /** Null for UNLIMITED promotions. */
    private Integer usageLimit;

    /** Changed only by the atomic usage updates in {@code PromotionRepository}, never through the entity. */
    @Column(nullable = false, insertable = false, updatable = false)
    private int usageCount;

    /** Percent in (0, 100] for PERCENTAGE, else null. */
    @Column(precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    /** Amount for FIXED, else null. */
    @Column(precision = 19, scale = 2)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal minimumOrderPurchaseAmount;

    @Column(nullable = false)
    private boolean isActive;

    @Column(nullable = false)
    private Instant startDate;

    @Column(nullable = false)
    private Instant endDate;

    @BatchSize(size = 100)
    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PromotionApply> promotionApplies = new ArrayList<>();

    /** Replaces the targets (orphan removal deletes the old rows). */
    public void replacePromotionApplies(List<PromotionApply> applies) {
        promotionApplies.clear();
        promotionApplies.addAll(applies);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Promotion other && id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
