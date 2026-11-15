package com.shopapp.promotion.viewmodel;

import com.shopapp.promotion.model.Promotion;
import com.shopapp.promotion.model.PromotionApply;
import com.shopapp.promotion.model.enumeration.ApplyTo;
import com.shopapp.promotion.model.enumeration.DiscountType;
import com.shopapp.promotion.model.enumeration.UsageType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * A promotion with its targets as ids (YAS returned brand / category / product objects fetched from product per
 * promotion; the back office resolves the names itself, decisions.md 4-44).
 */
public record PromotionDetailVm(
    Long id,
    String name,
    String slug,
    String description,
    String couponCode,
    Integer usageLimit,
    int usageCount,
    DiscountType discountType,
    ApplyTo applyTo,
    UsageType usageType,
    BigDecimal discountPercentage,
    BigDecimal discountAmount,
    BigDecimal minimumOrderPurchaseAmount,
    Boolean isActive,
    Instant startDate,
    Instant endDate,
    List<Long> productIds,
    List<Long> brandIds,
    List<Long> categoryIds
) {

    public static PromotionDetailVm fromModel(Promotion promotion) {
        return new PromotionDetailVm(
            promotion.getId(),
            promotion.getName(),
            promotion.getSlug(),
            promotion.getDescription(),
            promotion.getCouponCode(),
            promotion.getUsageLimit(),
            promotion.getUsageCount(),
            promotion.getDiscountType(),
            promotion.getApplyTo(),
            promotion.getUsageType(),
            promotion.getDiscountPercentage(),
            promotion.getDiscountAmount(),
            promotion.getMinimumOrderPurchaseAmount(),
            promotion.isActive(),
            promotion.getStartDate(),
            promotion.getEndDate(),
            ids(promotion, PromotionApply::getProductId),
            ids(promotion, PromotionApply::getBrandId),
            ids(promotion, PromotionApply::getCategoryId));
    }

    private static List<Long> ids(Promotion promotion, Function<PromotionApply, Long> id) {
        return promotion.getPromotionApplies().stream().map(id).filter(Objects::nonNull).sorted().toList();
    }
}
