package com.shopapp.product.viewmodel.product;

/**
 * Product card with price (YAS shape) plus stock: {@code stockAvailable} is true when not tracked or at least one unit
 * is left; {@code stockQuantity} is the tracked quantity (null when not tracked) — decisions.md 3c-24.
 */
public record ProductThumbnailGetVm(
    long id, String name, String slug, String thumbnailUrl, Double price,
    boolean stockTrackingEnabled, boolean stockAvailable, Long stockQuantity
) {
}
