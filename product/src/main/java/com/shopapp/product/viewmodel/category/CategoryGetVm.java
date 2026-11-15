package com.shopapp.product.viewmodel.category;

import com.shopapp.product.viewmodel.ImageVm;
import java.util.Map;

public record CategoryGetVm(long id, String name, String slug, long parentId, ImageVm categoryImage) {

    /** This category with its image URL from a media-service lookup (decisions.md 3b-19). */
    public CategoryGetVm withImageUrl(Map<Long, String> urls) {
        return new CategoryGetVm(id, name, slug, parentId, ImageVm.withUrlFrom(categoryImage, urls));
    }
}
