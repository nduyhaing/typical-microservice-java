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
        public static final String ADDRESS_CHOICE_REQUIRED = "ADDRESS_CHOICE_REQUIRED";
        public static final String ADDRESS_NOT_FOUND = "ADDRESS_NOT_FOUND";
        public static final String COUNTRY_NOT_FOUND = "COUNTRY_NOT_FOUND";
        public static final String STATE_NOT_IN_COUNTRY = "STATE_NOT_IN_COUNTRY";
        public static final String DISTRICT_NEEDS_STATE = "DISTRICT_NEEDS_STATE";
        public static final String DISTRICT_NOT_IN_STATE = "DISTRICT_NOT_IN_STATE";
        public static final String NOT_ENOUGH_STOCK = "NOT_ENOUGH_STOCK";
        public static final String PRODUCTS_NO_LONGER_AVAILABLE = "PRODUCTS_NO_LONGER_AVAILABLE";

        private ErrorCode() {
        }
    }
}
