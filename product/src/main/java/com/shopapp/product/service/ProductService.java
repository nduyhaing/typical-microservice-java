package com.shopapp.product.service;

import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.model.ProductCategory;
import com.shopapp.product.model.ProductRelated;
import com.shopapp.product.repository.BrandRepository;
import com.shopapp.product.repository.CategoryRepository;
import com.shopapp.product.repository.ProductCategoryRepository;
import com.shopapp.product.repository.ProductRelatedRepository;
import com.shopapp.product.repository.ProductRepository;
import com.shopapp.product.utils.Constants;
import com.shopapp.product.viewmodel.product.ProductFeatureGetVm;
import com.shopapp.product.viewmodel.product.ProductListGetFromCategoryVm;
import com.shopapp.product.viewmodel.product.ProductThumbnailGetVm;
import com.shopapp.product.viewmodel.product.ProductThumbnailVm;
import com.shopapp.product.viewmodel.product.ProductsGetVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Storefront product listings (ported from YAS ProductService).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRelatedRepository productRelatedRepository;
    private final MediaService mediaService;

    public ProductFeatureGetVm getListFeaturedProducts(int pageNo, int pageSize) {
        Page<Product> productPage = productRepository.getFeaturedProduct(PageRequest.of(pageNo, pageSize));
        return new ProductFeatureGetVm(productPage.map(this::toThumbnailGetVm).getContent(),
            productPage.getTotalPages());
    }

    public ProductsGetVm getProductsByMultiQuery(int pageNo, int pageSize, String productName, String categorySlug,
                                                 Double startPrice, Double endPrice) {
        Page<Product> productPage = productRepository.findByProductNameAndCategorySlugAndPriceBetween(
            productName.trim().toLowerCase(), categorySlug.trim(), startPrice, endPrice,
            PageRequest.of(pageNo, pageSize));
        return toProductsGetVm(productPage.map(this::toThumbnailGetVm));
    }

    public ProductListGetFromCategoryVm getProductsFromCategory(int pageNo, int pageSize, String categorySlug) {
        Category category = categoryRepository.findBySlug(categorySlug)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.CATEGORY_NOT_FOUND, categorySlug));
        Page<ProductThumbnailVm> page = productCategoryRepository
            .findStorefrontByCategory(category, PageRequest.of(pageNo, pageSize))
            .map(ProductCategory::getProduct)
            .map(this::toThumbnailVm);
        return new ProductListGetFromCategoryVm(page.getContent(), page.getNumber(), page.getSize(),
            (int) page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public List<ProductThumbnailVm> getProductsByBrand(String brandSlug) {
        Brand brand = brandRepository.findBySlug(brandSlug)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.BRAND_NOT_FOUND, brandSlug));
        return productRepository.findAllByBrandAndIsPublishedTrueAndIsVisibleIndividuallyTrueOrderByIdAsc(brand)
            .stream().map(this::toThumbnailVm).toList();
    }

    public ProductsGetVm getRelatedProductsStorefront(Long id, int pageNo, int pageSize) {
        Product product = productRepository.findByIdAndIsPublishedTrue(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, id));
        Page<ProductRelated> relatedPage = productRelatedRepository.findStorefrontRelated(product,
            PageRequest.of(pageNo, pageSize));
        List<ProductThumbnailGetVm> related = relatedPage.stream()
            .map(ProductRelated::getRelatedProduct)
            .map(this::toThumbnailGetVm)
            .toList();
        return new ProductsGetVm(related, relatedPage.getNumber(), relatedPage.getSize(),
            (int) relatedPage.getTotalElements(), relatedPage.getTotalPages(), relatedPage.isLast());
    }

    private ProductsGetVm toProductsGetVm(Page<ProductThumbnailGetVm> page) {
        return new ProductsGetVm(page.getContent(), page.getNumber(), page.getSize(), (int) page.getTotalElements(),
            page.getTotalPages(), page.isLast());
    }

    private ProductThumbnailGetVm toThumbnailGetVm(Product product) {
        return new ProductThumbnailGetVm(product.getId(), product.getName(), product.getSlug(),
            thumbnailUrl(product), product.getPrice());
    }

    private ProductThumbnailVm toThumbnailVm(Product product) {
        return new ProductThumbnailVm(product.getId(), product.getName(), product.getSlug(), thumbnailUrl(product));
    }

    private String thumbnailUrl(Product product) {
        return mediaService.getMedia(product.getThumbnailMediaId()).url();
    }
}
