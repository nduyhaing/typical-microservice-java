package com.shopapp.product.viewmodel.category;

import com.shopapp.product.viewmodel.ImageVm;

public record CategoryGetVm(long id, String name, String slug, long parentId, ImageVm categoryImage) {
}
