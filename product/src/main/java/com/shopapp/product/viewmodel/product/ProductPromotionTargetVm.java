package com.shopapp.product.viewmodel.product;

import java.util.List;

/**
 * What promotion needs to decide whether a coupon applies to an orderable product (decisions.md 4-74): a variation's
 * {@code brandId} is its own brand, else its parent's, and its {@code categoryIds} are its own and its parent's.
 */
public record ProductPromotionTargetVm(Long id, Long parentId, Long brandId, List<Long> categoryIds, Double price) {
}
