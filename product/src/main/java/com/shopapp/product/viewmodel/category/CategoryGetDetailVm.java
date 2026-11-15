package com.shopapp.product.viewmodel.category;

import com.shopapp.product.model.Category;
import com.shopapp.product.viewmodel.ImageVm;
import java.util.Map;

/**
 * Back office category detail (YAS shape; parentId 0 = top level). {@code displayOrder} is a {@code Short}: YAS's
 * primitive {@code short} threw a NullPointerException for categories saved without a display order.
 */
public record CategoryGetDetailVm(long id, String name, String slug, String description, long parentId,
                                  String metaKeywords, String metaDescription, Short displayOrder, Boolean isPublish,
                                  ImageVm categoryImage) {

    public static CategoryGetDetailVm fromModel(Category category, ImageVm categoryImage) {
        Category parent = category.getParent();
        long parentId = parent == null ? 0L : parent.getId();
        return new CategoryGetDetailVm(category.getId(), category.getName(), category.getSlug(),
            category.getDescription(), parentId, category.getMetaKeyword(), category.getMetaDescription(),
            category.getDisplayOrder(), category.getIsPublished(), categoryImage);
    }

    /** This category with its image URL from a media-service lookup (decisions.md 3b-19). */
    public CategoryGetDetailVm withImageUrl(Map<Long, String> urls) {
        return new CategoryGetDetailVm(id, name, slug, description, parentId, metaKeywords, metaDescription,
            displayOrder, isPublish, ImageVm.withUrlFrom(categoryImage, urls));
    }
}
