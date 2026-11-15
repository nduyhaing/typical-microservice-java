package com.shopapp.location.utils;

public final class Constants {

    private Constants() {
    }

    public static final class ErrorCode {

        public static final String COUNTRY_NOT_FOUND = "COUNTRY_NOT_FOUND";
        public static final String NAME_ALREADY_EXITED = "NAME_ALREADY_EXITED";
        public static final String STATE_OR_PROVINCE_NOT_FOUND = "STATE_OR_PROVINCE_NOT_FOUND";
        public static final String DISTRICT_NOT_FOUND = "DISTRICT_NOT_FOUND";
        public static final String ADDRESS_NOT_FOUND = "ADDRESS_NOT_FOUND";
        public static final String CODE_ALREADY_EXISTED = "CODE_ALREADY_EXISTED";
        public static final String COUNTRY_ID_REQUIRED = "COUNTRY_ID_REQUIRED";
        public static final String COUNTRY_IN_USE = "COUNTRY_IN_USE";
        public static final String STATE_OR_PROVINCE_IN_USE = "STATE_OR_PROVINCE_IN_USE";
        public static final String STATE_NOT_IN_COUNTRY = "STATE_NOT_IN_COUNTRY";
        public static final String DISTRICT_NOT_IN_STATE = "DISTRICT_NOT_IN_STATE";
        public static final String TOO_MANY_IDS = "TOO_MANY_IDS";

        private ErrorCode() {
        }
    }

    public static final class ApiConstant {

        public static final String STATE_OR_PROVINCES_URL = "/backoffice/state-or-provinces";
        public static final String STATE_OR_PROVINCES_STOREFRONT_URL = "/storefront/state-or-provinces";
        public static final String COUNTRIES_URL = "/backoffice/countries";
        public static final String COUNTRIES_STOREFRONT_URL = "/storefront/countries";
        public static final String ADDRESSES_URL = "/storefront/addresses";

        private ApiConstant() {
        }
    }

    /** Paging bounds like the product lists (decisions.md 2a-10); id lists at most 100 ids. */
    public static final class Limits {

        public static final long MAX_PAGE_NO = 10_000_000;
        public static final long MAX_PAGE_SIZE = 100;
        public static final int MAX_IDS = 100;

        private Limits() {
        }
    }
}
