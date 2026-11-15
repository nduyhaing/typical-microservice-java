package com.shopapp.product.viewmodel.product;

/** Product card (YAS shape) plus stock, as in {@link ProductThumbnailGetVm} (decisions.md 3c-24). */
public record ProductThumbnailVm(
    long id, String name, String slug, String thumbnailUrl,
    boolean stockTrackingEnabled, boolean stockAvailable, Long stockQuantity
) {
}
