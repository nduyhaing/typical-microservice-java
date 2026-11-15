package com.shopapp.cart.viewmodel;

import lombok.Builder;

/**
 * A product from product list-featured. {@code stockTrackingEnabled}/{@code stockQuantity} (decisions.md 3c-24)
 * cap the quantity in the cart (3c-47); boxed so a product service without them still parses.
 */
@Builder
public record ProductThumbnailVm(long id, String name, String slug, String thumbnailUrl, Boolean stockTrackingEnabled,
                                 Long stockQuantity) {

    /** Units that may be put in a cart; null when stock is not tracked (no limit besides the cart's own). */
    public Long availableQuantity() {
        if (!Boolean.TRUE.equals(stockTrackingEnabled)) {
            return null;
        }
        return stockQuantity == null ? 0L : Math.max(0L, stockQuantity);
    }
}
