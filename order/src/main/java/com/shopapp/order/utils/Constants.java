package com.shopapp.order.utils;

public final class Constants {

    /** Largest quantity of one product (same limit as the cart, decisions.md 2b-11). */
    public static final int MAX_QUANTITY = 1000;

    private Constants() {
    }

    /** Message codes resolved from messages/messages.properties. */
    public static final class ErrorCode {

        public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
        public static final String CHECKOUT_NOT_FOUND = "CHECKOUT_NOT_FOUND";
        public static final String CHECKOUT_NOT_YOURS = "CHECKOUT_NOT_YOURS";
        public static final String CHECKOUT_ALREADY_ORDERED = "CHECKOUT_ALREADY_ORDERED";
        public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
        public static final String ORDER_NOT_YOURS = "ORDER_NOT_YOURS";

        private ErrorCode() {
        }
    }
}
