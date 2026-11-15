package com.shopapp.search.model;

/** Catalog search order (YAS). DEFAULT: best match first when there is a keyword, then newest (decisions.md 4-15). */
public enum SortType {
    DEFAULT,
    PRICE_ASC,
    PRICE_DESC
}
