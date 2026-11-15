package com.shopapp.tax.utils;

public final class Constants {

    public static final class ErrorCode {
        public static final String TAX_CLASS_NOT_FOUND = "TAX_CLASS_NOT_FOUND";
        public static final String TAX_RATE_NOT_FOUND = "TAX_RATE_NOT_FOUND";
        public static final String NAME_ALREADY_EXITED = "NAME_ALREADY_EXITED";
        public static final String TAX_CLASS_IN_USE = "TAX_CLASS_IN_USE";
        public static final String TAX_RATE_ALREADY_EXISTED = "TAX_RATE_ALREADY_EXISTED";

        private ErrorCode() {
        }
    }

    /** Paging bounds: keep pageNo * pageSize within an int offset (decisions.md 2a-10). */
    public static final long MAX_PAGE_NO = 10_000_000;
    public static final long MAX_PAGE_SIZE = 100;
    /** At most this many tax classes per location-based-batch call. */
    public static final int MAX_BATCH_TAX_CLASSES = 100;
    public static final int MAX_ZIP_CODE_LENGTH = 25;

    private Constants() {
    }
}
