package com.shopapp.product.service;

import com.shopapp.product.model.Category;
import com.shopapp.product.repository.CategoryRepository;
import com.shopapp.product.viewmodel.ImageVm;
import com.shopapp.product.viewmodel.category.CategoryGetVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final MediaService mediaService;

    public List<CategoryGetVm> getCategories(String categoryName) {
        return categoryRepository.findByNameContainingIgnoreCase(categoryName).stream()
            .map(this::toVm)
            .toList();
    }

    private CategoryGetVm toVm(Category category) {
        ImageVm categoryImage = category.getImageId() == null ? null
            : new ImageVm(category.getImageId(), mediaService.getMedia(category.getImageId()).url());
        Category parent = category.getParent();
        long parentId = parent == null ? -1 : parent.getId();
        return new CategoryGetVm(category.getId(), category.getName(), category.getSlug(), parentId, categoryImage);
    }
}
