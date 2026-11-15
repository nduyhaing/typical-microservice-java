package com.shopapp.order.exception;

import lombok.Getter;

/**
 * Promotion refused a coupon (400): {@code code} is promotion's error code (e.g. PROMOTION_EXPIRED,
 * PROMOTION_USED_UP), {@code getMessage()} its readable text. Answered as 400 with the code as the ErrorVm title, like
 * promotion does, so the storefront can tell the reasons apart (decisions.md 4-55).
 */
@Getter
public class CouponRejectedException extends RuntimeException {

    private final String code;

    public CouponRejectedException(String code, String message) {
        super(message);
        this.code = code;
    }
}
