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
        public static final String PARENT_CATEGORY_NOT_FOUND = "PARENT_CATEGORY_NOT_FOUND";
        public static final String MAKE_SURE_CATEGORY_DO_NOT_CONTAIN_CHILDREN
            = "MAKE_SURE_CATEGORY_DO_NOT_CONTAIN_CHILDREN";
        public static final String MAKE_SURE_CATEGORY_DO_NOT_CONTAIN_PRODUCT
            = "MAKE_SURE_CATEGORY_DO_NOT_CONTAIN_PRODUCT";
        public static final String PARENT_CATEGORY_CANNOT_BE_ITSELF = "PARENT_CATEGORY_CANNOT_BE_ITSELF";
        public static final String MAKE_SURE_BRAND_DONT_CONTAINS_ANY_PRODUCT
            = "MAKE_SURE_BRAND_DONT_CONTAINS_ANY_PRODUCT";
        public static final String NAME_ALREADY_EXITED = "NAME_ALREADY_EXITED";
        public static final String SLUG_ALREADY_EXISTED_OR_DUPLICATED = "SLUG_ALREADY_EXISTED_OR_DUPLICATED";
        public static final String SKU_ALREADY_EXISTED_OR_DUPLICATED = "SKU_ALREADY_EXISTED_OR_DUPLICATED";
        public static final String GTIN_ALREADY_EXISTED_OR_DUPLICATED = "GTIN_ALREADY_EXISTED_OR_DUPLICATED";
        public static final String PRODUCT_OPTION_NOT_FOUND = "PRODUCT_OPTION_NOT_FOUND";
        public static final String PRODUCT_OPTION_VALUE_IS_NOT_FOUND = "PRODUCT_OPTION_VALUE_IS_NOT_FOUND";
        public static final String NO_MATCHING_PRODUCT_OPTIONS = "NO_MATCHING_PRODUCT_OPTIONS";
        public static final String MAKE_SURE_LENGTH_GREATER_THAN_WIDTH = "MAKE_SURE_LENGTH_GREATER_THAN_WIDTH";
        public static final String EDIT_THE_PARENT_PRODUCT = "EDIT_THE_PARENT_PRODUCT";

        private ErrorCode() {
        }
    }
}
