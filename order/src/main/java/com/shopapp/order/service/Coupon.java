package com.shopapp.order.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * A coupon promotion accepted for a checkout (decisions.md 4-50): the code as promotion knows it, the discount type
 * and value (percent for PERCENTAGE, an amount for FIXED) and the products of the checkout it applies to, in the
 * checkout's order.
 */
public record Coupon(String code, Type type, BigDecimal value, List<Long> eligibleProductIds) {

    /** Promotion's discount types. */
    public enum Type {
        PERCENTAGE, FIXED
    }

    public Coupon {
        eligibleProductIds = List.copyOf(eligibleProductIds);
    }
}
