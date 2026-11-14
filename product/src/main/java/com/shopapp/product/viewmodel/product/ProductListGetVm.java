package com.shopapp.product.viewmodel.product;

import java.util.List;

/** A page of the back office product list (YAS shape). */
public record ProductListGetVm(
    List<ProductListVm> productContent,
    int pageNo,
    int pageSize,
    int totalElements,
    int totalPages,
    boolean isLast
) {
}
