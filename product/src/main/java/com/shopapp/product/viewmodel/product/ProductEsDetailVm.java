package com.shopapp.product.viewmodel.product;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * What the search service indexes (YAS ProductEsDetailVm; {@code attributes} are attribute names as YAS). Added:
 * {@code thumbnailUrl} (search results show the image without a media call), {@code createdOn} (YAS sorts by it
 * but never filled it) and {@code lastModifiedOn} (search's document version, decisions.md 4-10, 4-13).
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
    ZonedDateTime createdOn,
    ZonedDateTime lastModifiedOn) {

    public ProductEsDetailVm withThumbnailUrl(String url) {
        return new ProductEsDetailVm(id, name, slug, price, isPublished, isVisibleIndividually, isAllowedToOrder,
            isFeatured, thumbnailMediaId, url, brand, categories, attributes, createdOn, lastModifiedOn);
    }
}
