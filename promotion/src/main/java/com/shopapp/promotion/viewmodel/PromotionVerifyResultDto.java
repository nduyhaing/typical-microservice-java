package com.shopapp.promotion.viewmodel;

import com.shopapp.promotion.model.enumeration.DiscountType;
import java.math.BigDecimal;
import java.util.List;

/**
 * A valid coupon (YAS fields isValid, productId, couponCode, discountType, discountValue) plus every eligible
 * product id of the request, so the order can discount eligible lines only. {@code productId} is the cheapest
 * eligible product (YAS); {@code discountValue} is the percentage for PERCENTAGE, the amount for FIXED.
 */
public record PromotionVerifyResultDto(
    boolean isValid,
    Long productId,
    List<Long> productIds,
    String couponCode,
    DiscountType discountType,
    BigDecimal discountValue,
    Long promotionId,
    String promotionName
) {
}
