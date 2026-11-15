package com.shopapp.promotion.viewmodel;

import com.shopapp.promotion.model.enumeration.ApplyTo;
import com.shopapp.promotion.model.enumeration.DiscountType;
import com.shopapp.promotion.model.enumeration.UsageType;
import jakarta.validation.constraints.AssertTrue;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The editable fields of a promotion (create and update) with the cross-field rules of YAS PromotionValidator plus the
 * date order (YAS checked it in the service): field errors {@code dateRangeValid}, {@code discountValid},
 * {@code usageLimitValid}, {@code targetsValid}.
 */
public interface PromotionInput {

    String name();

    String slug();

    String description();

    String couponCode();

    DiscountType discountType();

    BigDecimal discountPercentage();

    BigDecimal discountAmount();

    UsageType usageType();

    Integer usageLimit();

    BigDecimal minimumOrderPurchaseAmount();

    Boolean isActive();

    Instant startDate();

    Instant endDate();

    ApplyTo applyTo();

    List<Long> productIds();

    List<Long> brandIds();

    List<Long> categoryIds();

    @AssertTrue(message = "end date must be after start date")
    default boolean isDateRangeValid() {
        return startDate() == null || endDate() == null || endDate().isAfter(startDate());
    }

    /** PERCENTAGE needs a percentage, FIXED an amount (ranges are checked on the fields). */
    @AssertTrue(message = "discount value is required for the discount type")
    default boolean isDiscountValid() {
        if (discountType() == null) {
            return true;
        }
        return discountType() == DiscountType.PERCENTAGE ? discountPercentage() != null : discountAmount() != null;
    }

    @AssertTrue(message = "a limited promotion needs a usage limit of at least 1")
    default boolean isUsageLimitValid() {
        return usageType() != UsageType.LIMITED || usageLimit() != null && usageLimit() >= 1;
    }

    @AssertTrue(message = "at least one product, brand or category matching applyTo is required")
    default boolean isTargetsValid() {
        if (applyTo() == null) {
            return true;
        }
        List<Long> targets = targetIds();
        return targets != null && !targets.isEmpty();
    }

    /** The ids for {@link #applyTo()}; the other two lists are ignored. */
    default List<Long> targetIds() {
        return switch (applyTo()) {
            case PRODUCT -> productIds();
            case BRAND -> brandIds();
            case CATEGORY -> categoryIds();
        };
    }
}
