package com.shopapp.order.viewmodel.external;

import java.math.BigDecimal;
import java.util.List;

/**
 * Promotion's answer to a valid coupon (its PromotionVerifyResultDto, decisions.md 4-43): {@code productIds} are the
 * eligible products of the request, {@code discountValue} the percent (PERCENTAGE) or the amount (FIXED).
 */
public record PromotionVerifyResultVm(List<Long> productIds, String couponCode, String discountType,
                                      BigDecimal discountValue, Long promotionId, String promotionName) {
}
