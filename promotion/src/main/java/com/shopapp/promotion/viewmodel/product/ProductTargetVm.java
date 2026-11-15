package com.shopapp.promotion.viewmodel.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * One orderable product from product's {@code GET /storefront/products/promotion-targets}: a variation's brand is its
 * own or its parent's, its categories its own and its parent's (decisions.md 4-74).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductTargetVm(Long id, Long parentId, Long brandId, List<Long> categoryIds, Double price) {

    public List<Long> categoryIds() {
        return categoryIds == null ? List.of() : categoryIds;
    }
}
