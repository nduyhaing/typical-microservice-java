package com.shopapp.product.utils;

public final class Constants {

    private Constants() {
    }

    /** Message codes resolved from messages/messages.properties. */
    public static final class ErrorCode {

        public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
        public static final String CATEGORY_NOT_FOUND = "CATEGORY_NOT_FOUND";
        public static final String BRAND_NOT_FOUND = "BRAND_NOT_FOUND";
        public static final String PRODUCT_NOT_HAVE_VARIATION = "PRODUCT_NOT_HAVE_VARIATION";

        private ErrorCode() {
        }
    }
}
