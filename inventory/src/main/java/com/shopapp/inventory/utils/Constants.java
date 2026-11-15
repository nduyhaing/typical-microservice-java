package com.shopapp.inventory.utils;

public final class Constants {

    public static final class ErrorCode {
        public static final String WAREHOUSE_NOT_FOUND = "WAREHOUSE_NOT_FOUND";
        public static final String STOCK_NOT_FOUND = "STOCK_NOT_FOUND";
        public static final String PRODUCT_NOT_FOUND = "PRODUCT_NOT_FOUND";
        public static final String NAME_ALREADY_EXITED = "NAME_ALREADY_EXITED";
        public static final String STOCK_ALREADY_EXISTED = "STOCK_ALREADY_EXISTED";
        public static final String INVALID_ADJUSTED_QUANTITY = "INVALID_ADJUSTED_QUANTITY";
        public static final String PRODUCT_STOCK_TRACKING_DISABLED = "PRODUCT_STOCK_TRACKING_DISABLED";
        public static final String DUPLICATED_ITEM_IN_LIST = "DUPLICATED_ITEM_IN_LIST";
        public static final String WAREHOUSE_HAS_STOCK = "WAREHOUSE_HAS_STOCK";
        public static final String INVALID_ADDRESS = "INVALID_ADDRESS";
        public static final String ADDRESS_NOT_FOUND = "ADDRESS_NOT_FOUND";

        private ErrorCode() {
        }
    }

    /** Paging bounds: keep pageNo * pageSize within an int offset (decisions.md 2a-10). */
    public static final long MAX_PAGE_NO = 10_000_000;
    public static final long MAX_PAGE_SIZE = 100;
    /** At most this many items per add-products or adjust call. */
    public static final int MAX_ITEMS = 100;

    private Constants() {
    }
}
