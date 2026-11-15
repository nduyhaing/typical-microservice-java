package com.shopapp.promotion.viewmodel.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** A page of product's {@code GET /storefront/category/{slug}/products}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CategoryProductsPageVm(List<ProductIdVm> productContent, int totalPages) {
}
