package com.shopapp.promotion.utils;

public final class Constants {

    public static final class ErrorCode {
        public static final String PROMOTION_NOT_FOUND = "PROMOTION_NOT_FOUND";
        public static final String PROMOTION_NOT_STARTED = "PROMOTION_NOT_STARTED";
        public static final String PROMOTION_EXPIRED = "PROMOTION_EXPIRED";
        public static final String PROMOTION_USED_UP = "PROMOTION_USED_UP";
        public static final String MINIMUM_ORDER_NOT_REACHED = "MINIMUM_ORDER_NOT_REACHED";
        public static final String PROMOTION_NOT_APPLICABLE = "PROMOTION_NOT_APPLICABLE";
        public static final String PROMOTION_IN_USE = "PROMOTION_IN_USE";
        public static final String COUPON_CODE_ALREADY_EXISTED = "COUPON_CODE_ALREADY_EXISTED";
        public static final String SLUG_ALREADY_EXISTED = "SLUG_ALREADY_EXISTED";
        public static final String USAGE_LIMIT_BELOW_USAGE_COUNT = "USAGE_LIMIT_BELOW_USAGE_COUNT";
        public static final String USAGE_REFERENCE_CONFLICT = "USAGE_REFERENCE_CONFLICT";

        private ErrorCode() {
        }
    }

    /** Paging bounds: keep pageNo * pageSize within an int offset (decisions.md 2a-10). */
    public static final long MAX_PAGE_NO = 10_000_000;
    public static final long MAX_PAGE_SIZE = 100;
    /** Products per verify / usage call, and target products per promotion. */
    public static final int MAX_IDS = 100;
    /** Target brands or categories per promotion (decisions.md 4-74). */
    public static final int MAX_GROUP_TARGETS = 50;
    public static final int MAX_FILTER_LENGTH = 450;

    private Constants() {
    }
}
