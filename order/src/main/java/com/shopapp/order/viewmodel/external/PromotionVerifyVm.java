package com.shopapp.order.viewmodel.external;

import java.math.BigDecimal;
import java.util.List;

/** Body of promotion POST /storefront/promotions/verify: the code, the pre-discount subtotal and the products. */
public record PromotionVerifyVm(String couponCode, BigDecimal orderPrice, List<Long> productIds) {
}
