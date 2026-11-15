package com.shopapp.search.viewmodel;

import com.shopapp.search.model.Product;
import java.time.Instant;

/** One search hit (YAS ProductGetVm plus thumbnailUrl). */
public record ProductGetVm(Long id,
                           String name,
                           String slug,
                           Long thumbnailId,
                           String thumbnailUrl,
                           Double price,
                           Boolean isAllowedToOrder,
                           Boolean isPublished,
                           Boolean isFeatured,
                           Boolean isVisibleIndividually,
                           Instant createdOn) {

    public static ProductGetVm fromModel(Product product) {
        return new ProductGetVm(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.getThumbnailMediaId(),
            product.getThumbnailUrl(),
            product.getPrice(),
            product.getIsAllowedToOrder(),
            product.getIsPublished(),
            product.getIsFeatured(),
            product.getIsVisibleIndividually(),
            product.getCreatedOn());
    }
}
