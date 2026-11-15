package com.shopapp.promotion.viewmodel.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The fields promotion reads from product's {@code GET /products?ids=} (orderable products only). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductCheckoutVm(Long id, Long parentId, Long brandId, Double price) {
}
