package com.shopapp.recommendation.viewmodel;

/**
 * One similar product (YAS RelatedProductVm, reduced to what a storefront product card shows: YAS's description,
 * meta fields, specification and image list are not sent; {@code thumbnailUrl} replaces YAS's {@code thumbnail}
 * object, decisions.md 5-43).
 */
public record RelatedProductVm(long id, String name, String slug, Double price, String brandName,
                               String thumbnailUrl) {
}
