package com.shopapp.cart.utils;

public final class Constants {

    private Constants() {
    }


    /** Largest quantity of one product in a cart (decisions.md 2b-11). */
    public static final int MAX_CART_QUANTITY = 1000;

    public static final class ErrorCode {

        private ErrorCode() {
        }

        public static final String NOT_FOUND_PRODUCT = "NOT_FOUND_PRODUCT";
        public static final String NOT_EXISTING_ITEM_IN_CART = "NOT_EXISTING_ITEM_IN_CART";
        public static final String NOT_EXISTING_PRODUCT_IN_CART = "NOT_EXISTING_PRODUCT_IN_CART";
        public static final String NON_EXISTING_CART_ITEM = "NON_EXISTING_CART_ITEM";
        public static final String ADD_CART_ITEM_FAILED = "ADD_CART_ITEM_FAILED";
        public static final String DUPLICATED_CART_ITEMS_TO_DELETE = "DUPLICATED_CART_ITEMS_TO_DELETE";
        public static final String QUANTITY_TOO_LARGE = "QUANTITY_TOO_LARGE";
        public static final String NOT_ENOUGH_STOCK = "NOT_ENOUGH_STOCK";
        public static final String OUT_OF_STOCK = "OUT_OF_STOCK";
    }
}
