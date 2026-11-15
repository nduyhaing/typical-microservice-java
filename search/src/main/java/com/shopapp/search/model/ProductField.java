package com.shopapp.search.model;

/** Field names of the {@link Product} document used in queries (YAS ProductField). */
public final class ProductField {

    public static final String NAME = "name";
    public static final String BRAND = "brand";
    public static final String PRICE = "price";
    public static final String IS_PUBLISHED = "isPublished";
    public static final String IS_VISIBLE_INDIVIDUALLY = "isVisibleIndividually";
    public static final String CATEGORIES = "categories";
    public static final String ATTRIBUTES = "attributes";
    public static final String CREATED_ON = "createdOn";

    private ProductField() {
    }
}
