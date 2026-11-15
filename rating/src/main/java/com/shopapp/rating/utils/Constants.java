package com.shopapp.rating.utils;

public final class Constants {

    public static final class ErrorCode {
        public static final String RATING_NOT_FOUND = "RATING_NOT_FOUND";
        public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
        public static final String RATING_NOT_ALLOWED = "RATING_NOT_ALLOWED";
        public static final String RATING_ALREADY_EXISTED = "RATING_ALREADY_EXISTED";
        public static final String RATING_NOT_YOURS = "RATING_NOT_YOURS";

        private ErrorCode() {
        }
    }

    /** Paging bounds: keep pageNo * pageSize within an int offset (decisions.md 2a-10). */
    public static final long MAX_PAGE_NO = 10_000_000;
    /** The product page asks for a few ratings at a time. */
    public static final long MAX_STOREFRONT_PAGE_SIZE = 50;
    public static final long MAX_PAGE_SIZE = 100;
    /** Most ratings on the back-office dashboard card. */
    public static final long MAX_LATEST_COUNT = 50;
    public static final int MAX_CONTENT_LENGTH = 1000;
    public static final int MAX_FILTER_LENGTH = 255;

    private Constants() {
    }
}
