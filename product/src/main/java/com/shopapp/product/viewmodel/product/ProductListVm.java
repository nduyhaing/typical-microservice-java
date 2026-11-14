package com.shopapp.product.viewmodel.product;

import com.shopapp.product.model.Product;
import java.time.ZonedDateTime;

/** One row of the back office product list (YAS shape). */
public record ProductListVm(Long id,
                            String name,
                            String slug,
                            Boolean isAllowedToOrder,
                            Boolean isPublished,
                            Boolean isFeatured,
                            Boolean isVisibleIndividually,
                            Double price,
                            ZonedDateTime createdOn,
                            Long taxClassId,
                            Long parentId) {

    public static ProductListVm fromModel(Product product) {
        return new ProductListVm(
            product.getId(),
            product.getName(),
            product.getSlug(),
            product.isAllowedToOrder(),
            product.isPublished(),
            product.isFeatured(),
            product.isVisibleIndividually(),
            product.getPrice(),
            product.getCreatedOn(),
            product.getTaxClassId(),
            product.getParent() == null ? null : product.getParent().getId());
    }
}
