package com.shopapp.webhook.utils;

public final class Constants {

    public static final class ErrorCode {
        public static final String WEBHOOK_NOT_FOUND = "WEBHOOK_NOT_FOUND";
        public static final String EVENT_NOT_FOUND = "EVENT_NOT_FOUND";
        public static final String WEBHOOK_URL_INVALID = "WEBHOOK_URL_INVALID";
        public static final String WEBHOOK_URL_SCHEME = "WEBHOOK_URL_SCHEME";
        public static final String WEBHOOK_URL_CREDENTIALS = "WEBHOOK_URL_CREDENTIALS";
        public static final String WEBHOOK_URL_UNRESOLVABLE = "WEBHOOK_URL_UNRESOLVABLE";
        public static final String WEBHOOK_URL_BLOCKED = "WEBHOOK_URL_BLOCKED";
        public static final String WEBHOOK_CONTENT_TYPE = "WEBHOOK_CONTENT_TYPE";
        public static final String WEBHOOK_LIMIT = "WEBHOOK_LIMIT";
        public static final String WEBHOOK_SECRET_REQUIRED = "WEBHOOK_SECRET_REQUIRED";
        public static final String WEBHOOK_INACTIVE = "WEBHOOK_INACTIVE";

        private ErrorCode() {
        }
    }

    /** Paging bounds: keep pageNo * pageSize within an int offset (decisions.md 2a-10). */
    public static final long MAX_PAGE_NO = 10_000_000;
    public static final long MAX_PAGE_SIZE = 100;
    /** At most this many webhooks (each event fans out to every subscriber), decisions.md 5-31. */
    public static final int MAX_WEBHOOKS = 50;
    public static final int MAX_URL_LENGTH = 2048;
    public static final int MIN_SECRET_LENGTH = 16;
    public static final int MAX_SECRET_LENGTH = 255;
    public static final String CONTENT_TYPE_JSON = "application/json";

    private Constants() {
    }
}
