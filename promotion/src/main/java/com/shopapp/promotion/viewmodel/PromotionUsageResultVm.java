package com.shopapp.promotion.viewmodel;

import java.util.UUID;

/** The use taken by apply-usage (the same one again when the reference is repeated). */
public record PromotionUsageResultVm(Long promotionId, String couponCode, UUID reference) {
}
