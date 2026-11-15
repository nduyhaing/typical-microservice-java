package com.shopapp.product.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.DuplicatedException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.product.model.Category;
import com.shopapp.product.repository.CategoryRepository;
import com.shopapp.product.repository.ProductCategoryRepository;
import com.shopapp.product.utils.Constants;
import com.shopapp.product.viewmodel.ImageVm;
import com.shopapp.product.viewmodel.category.CategoryGetDetailVm;
import com.shopapp.product.viewmodel.category.CategoryGetVm;
import com.shopapp.product.viewmodel.category.CategoryPostVm;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final MediaService mediaService;

    public List<CategoryGetVm> getCategories(String categoryName) {
        List<Category> categories = categoryRepository.findByNameContainingIgnoreCase(categoryName);
        Map<Long, String> urls = mediaService.getMediaUrls(categories.stream().map(Category::getImageId).toList());
        return categories.stream().map(category -> toVm(category, urls)).toList();
    }

    public CategoryGetDetailVm getCategoryById(Long id) {
        Category category = findCategory(id);
        return CategoryGetDetailVm.fromModel(category, image(category));
    }

    @Transactional
    public CategoryGetDetailVm create(CategoryPostVm categoryPostVm) {
        validateUnique(categoryPostVm, null);
        Category category = new Category();
        apply(category, categoryPostVm);
        if (categoryPostVm.parentId() != null) {
            category.setParent(findParent(categoryPostVm.parentId()));
        }
        return CategoryGetDetailVm.fromModel(categoryRepository.save(category), null);
    }

    @Transactional
    public void update(CategoryPostVm categoryPostVm, Long id) {
        Category category = findCategory(id);
        validateUnique(categoryPostVm, id);
        apply(category, categoryPostVm);
        if (categoryPostVm.parentId() == null) {
            category.setParent(null);
        } else {
            Category parent = findParent(categoryPostVm.parentId());
            if (isSelfOrAncestor(id, parent)) {
                throw new BadRequestException(Constants.ErrorCode.PARENT_CATEGORY_CANNOT_BE_ITSELF);
            }
            category.setParent(parent);
        }
        categoryRepository.save(category);
    }

    /** YAS refuses to delete a category with sub-categories or products. */
    @Transactional
    public void delete(Long id) {
        Category category = findCategory(id);
        if (categoryRepository.existsByParentId(id)) {
            throw new BadRequestException(Constants.ErrorCode.MAKE_SURE_CATEGORY_DO_NOT_CONTAIN_CHILDREN);
        }
        if (productCategoryRepository.existsByCategoryId(id)) {
            throw new BadRequestException(Constants.ErrorCode.MAKE_SURE_CATEGORY_DO_NOT_CONTAIN_PRODUCT);
        }
        categoryRepository.delete(category);
    }

    private static void apply(Category category, CategoryPostVm categoryPostVm) {
        category.setName(categoryPostVm.name());
        category.setSlug(categoryPostVm.slug());
        category.setDescription(categoryPostVm.description());
        category.setDisplayOrder(categoryPostVm.displayOrder());
        category.setMetaDescription(categoryPostVm.metaDescription());
        category.setMetaKeyword(categoryPostVm.metaKeywords());
        category.setIsPublished(categoryPostVm.isPublish());
        category.setImageId(categoryPostVm.imageId());
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.CATEGORY_NOT_FOUND, id));
    }

    private Category findParent(Long parentId) {
        return categoryRepository.findById(parentId)
            .orElseThrow(() -> new BadRequestException(Constants.ErrorCode.PARENT_CATEGORY_NOT_FOUND, parentId));
    }

    /** True when the would-be parent is the category itself or one of its descendants (a cycle). */
    private static boolean isSelfOrAncestor(Long id, Category parent) {
        for (Category current = parent; current != null; current = current.getParent()) {
            if (id.equals(current.getId())) {
                return true;
            }
        }
        return false;
    }

    /** YAS checks only the name; slugs must be unique too (they address the storefront category page). */
    private void validateUnique(CategoryPostVm categoryPostVm, Long id) {
        boolean nameTaken = id == null ? categoryRepository.existsByName(categoryPostVm.name())
            : categoryRepository.existsByNameAndIdNot(categoryPostVm.name(), id);
        if (nameTaken) {
            throw new DuplicatedException(Constants.ErrorCode.NAME_ALREADY_EXITED, categoryPostVm.name());
        }
        boolean slugTaken = id == null ? categoryRepository.existsBySlug(categoryPostVm.slug())
            : categoryRepository.existsBySlugAndIdNot(categoryPostVm.slug(), id);
        if (slugTaken) {
            throw new DuplicatedException(Constants.ErrorCode.SLUG_ALREADY_EXISTED_OR_DUPLICATED,
                categoryPostVm.slug());
        }
    }

    private ImageVm image(Category category) {
        return category.getImageId() == null ? null
            : new ImageVm(category.getImageId(), mediaService.getMedia(category.getImageId()).url());
    }

    private CategoryGetVm toVm(Category category, Map<Long, String> urls) {
        Category parent = category.getParent();
        long parentId = parent == null ? -1 : parent.getId();
        Long imageId = category.getImageId();
        ImageVm image = imageId == null ? null : new ImageVm(imageId, MediaService.urlOf(urls, imageId));
        return new CategoryGetVm(category.getId(), category.getName(), category.getSlug(), parentId, image);
    }
}
