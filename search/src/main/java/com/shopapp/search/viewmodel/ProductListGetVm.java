package com.shopapp.search.viewmodel;

import java.util.List;
import java.util.Map;

/**
 * A page of search results with facet counts (YAS shape): {@code aggregations} has {@code brands},
 * {@code categories} and {@code attributes}, each value → number of matching products.
 */
public record ProductListGetVm(
    List<ProductGetVm> products,
    int pageNo,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean isLast,
    Map<String, Map<String, Long>> aggregations) {
}
