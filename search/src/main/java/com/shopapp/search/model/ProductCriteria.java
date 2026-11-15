package com.shopapp.search.model;

import java.util.List;

/**
 * Validated catalog search input (YAS ProductCriteriaDto). Brand, category and attribute are lists of exact values
 * (any of them matches); a blank keyword matches every product.
 */
public record ProductCriteria(String keyword,
                              int page,
                              int size,
                              List<String> brands,
                              List<String> categories,
                              List<String> attributes,
                              Double minPrice,
                              Double maxPrice,
                              SortType sortType) {
}
