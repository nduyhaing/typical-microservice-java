package com.shopapp.recommendation.viewmodel;

import java.time.Instant;
import java.util.List;

/**
 * Product's {@code GET /storefront/products-es/{id}} (decisions.md 4-10): the gate for embedding a product, since it
 * answers 404 unless the product is published and visible individually; also gives slug, price, thumbnail URL and
 * the version ({@code lastModifiedOn}).
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
