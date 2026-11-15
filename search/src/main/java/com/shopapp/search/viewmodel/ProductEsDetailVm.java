package com.shopapp.search.viewmodel;

import java.time.Instant;
import java.util.List;

/**
 * Product's {@code GET /storefront/products-es/{id}} (YAS ProductEsDetailVm plus thumbnailUrl, createdOn and
 * lastModifiedOn, decisions.md 4-10).
 */
public record ProductEsDetailVm(
    Long id,
    String name,
    String slug,
    Double price,
    boolean isPublished,
    boolean isVisibleIndividually,
    boolean isAllowedToOrder,
    boolean isFeatured,
    Long thumbnailMediaId,
    String thumbnailUrl,
    String brand,
    List<String> categories,
    List<String> attributes,
    Instant createdOn,
    Instant lastModifiedOn) {
}
