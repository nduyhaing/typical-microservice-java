package com.shopapp.order.viewmodel.external;

import java.util.List;

/** Body of promotion POST /storefront/promotions/apply-usage: the coupon and the discounted products (4-46). */
public record PromotionApplyUsageVm(String couponCode, List<Long> productIds) {
}
