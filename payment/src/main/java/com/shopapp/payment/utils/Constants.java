package com.shopapp.payment.utils;

public final class Constants {

    public static final class ErrorCode {
        public static final String ORDER_NOT_FOUND = "ORDER_NOT_FOUND";
        public static final String ORDER_NOT_PAYABLE = "ORDER_NOT_PAYABLE";
        public static final String ORDER_ALREADY_PAID = "ORDER_ALREADY_PAID";
        public static final String PAYMENT_NOT_FOUND = "PAYMENT_NOT_FOUND";
        public static final String PAYMENT_PROVIDER_NOT_FOUND = "PAYMENT_PROVIDER_NOT_FOUND";
        public static final String PAYMENT_PROVIDER_ALREADY_EXISTS = "PAYMENT_PROVIDER_ALREADY_EXISTS";
        public static final String PAYMENT_METHOD_NOT_AVAILABLE = "PAYMENT_METHOD_NOT_AVAILABLE";
        public static final String PAYMENT_NOT_CONFIRMABLE = "PAYMENT_NOT_CONFIRMABLE";
        public static final String PAYMENT_NOT_CAPTURABLE = "PAYMENT_NOT_CAPTURABLE";
        public static final String PAYMENT_VERIFICATION_FAILED = "PAYMENT_VERIFICATION_FAILED";
        public static final String ORDER_SERVICE_UNAVAILABLE = "ORDER_SERVICE_UNAVAILABLE";
        public static final String PAYPAL_UNAVAILABLE = "PAYPAL_UNAVAILABLE";
        public static final String PAYPAL_NOT_CONFIGURED = "PAYPAL_NOT_CONFIGURED";
        public static final String PAYMENT_NOT_RECORDED = "PAYMENT_NOT_RECORDED";
        public static final String PAYMENT_REPLACED = "PAYMENT_REPLACED";

        private ErrorCode() {
        }
    }

    /** Paging bounds: keep pageNo * pageSize within an int offset (decisions.md 2a-10). */
    public static final int MAX_PAGE_NO = 10_000_000;
    public static final int MAX_PAGE_SIZE = 100;
    /** The only currency PayPal is charged in (decisions.md 5-25). */
    public static final String CURRENCY = "USD";

    private Constants() {
    }
}
