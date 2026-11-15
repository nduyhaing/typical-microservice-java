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
import com.shopapp.product.viewmodel.product.ProductCheckoutListVm;
import com.shopapp.product.viewmodel.product.ProductFeatureGetVm;
import com.shopapp.product.viewmodel.product.ProductGetCheckoutListVm;
import com.shopapp.product.viewmodel.product.ProductListGetFromCategoryVm;
import com.shopapp.product.viewmodel.product.ProductThumbnailGetVm;
import com.shopapp.product.viewmodel.product.ProductThumbnailVm;
import com.shopapp.product.viewmodel.product.ProductsGetVm;
import java.util.List;
import java.util.Map;
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
        Map<Long, String> urls = thumbnailUrls(productPage.getContent());
        return new ProductFeatureGetVm(productPage.map(product -> toThumbnailGetVm(product, urls)).getContent(),
            productPage.getTotalPages());
    }

    public ProductsGetVm getProductsByMultiQuery(int pageNo, int pageSize, String productName, String categorySlug,
                                                 Double startPrice, Double endPrice) {
        Page<Product> productPage = productRepository.findByProductNameAndCategorySlugAndPriceBetween(
            productName.trim().toLowerCase(), categorySlug.trim(), startPrice, endPrice,
            PageRequest.of(pageNo, pageSize));
        Map<Long, String> urls = thumbnailUrls(productPage.getContent());
        return toProductsGetVm(productPage.map(product -> toThumbnailGetVm(product, urls)));
    }

    public ProductListGetFromCategoryVm getProductsFromCategory(int pageNo, int pageSize, String categorySlug) {
        Category category = categoryRepository.findBySlug(categorySlug)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.CATEGORY_NOT_FOUND, categorySlug));
        Page<Product> products = productCategoryRepository
            .findStorefrontByCategory(category, PageRequest.of(pageNo, pageSize))
            .map(ProductCategory::getProduct);
        Map<Long, String> urls = thumbnailUrls(products.getContent());
        Page<ProductThumbnailVm> page = products.map(product -> toThumbnailVm(product, urls));
        return new ProductListGetFromCategoryVm(page.getContent(), page.getNumber(), page.getSize(),
            (int) page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public List<ProductThumbnailVm> getProductsByBrand(String brandSlug) {
        Brand brand = brandRepository.findBySlug(brandSlug)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.BRAND_NOT_FOUND, brandSlug));
        List<Product> products =
            productRepository.findAllByBrandAndIsPublishedTrueAndIsVisibleIndividuallyTrueOrderByIdAsc(brand);
        Map<Long, String> urls = thumbnailUrls(products);
        return products.stream().map(product -> toThumbnailVm(product, urls)).toList();
    }

    /**
     * Thumbnails (with price) of the requested published products; unknown or hidden ids, and variations of an
     * unpublished parent, are left out (decisions.md 3a-33).
     */
    public List<ProductThumbnailGetVm> getFeaturedProductsById(List<Long> productIds) {
        List<Product> products = productRepository.findStorefrontByIds(productIds);
        Map<Long, String> urls = thumbnailUrls(products);
        return products.stream().map(product -> toThumbnailGetVm(product, urls)).toList();
    }

    /** YAS GET /products: the order service's price lookup at checkout, orderable products only. */
    public ProductGetCheckoutListVm getProductCheckoutList(int pageNo, int pageSize, List<Long> productIds) {
        Page<Product> page = productRepository.findOrderableByIds(productIds, PageRequest.of(pageNo, pageSize));
        Map<Long, String> urls = thumbnailUrls(page.getContent());
        return new ProductGetCheckoutListVm(
            page.map(product -> ProductCheckoutListVm.fromModel(product,
                MediaService.urlOf(urls, product.getThumbnailMediaId()))).getContent(),
            page.getNumber(), page.getSize(), (int) page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public ProductsGetVm getRelatedProductsStorefront(Long id, int pageNo, int pageSize) {
        Product product = productRepository.findStorefrontById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, id));
        Page<ProductRelated> relatedPage = productRelatedRepository.findStorefrontRelated(product,
            PageRequest.of(pageNo, pageSize));
        List<Product> relatedProducts = relatedPage.stream().map(ProductRelated::getRelatedProduct).toList();
        Map<Long, String> urls = thumbnailUrls(relatedProducts);
        List<ProductThumbnailGetVm> related = relatedProducts.stream()
            .map(relatedProduct -> toThumbnailGetVm(relatedProduct, urls))
            .toList();
        return new ProductsGetVm(related, relatedPage.getNumber(), relatedPage.getSize(),
            (int) relatedPage.getTotalElements(), relatedPage.getTotalPages(), relatedPage.isLast());
    }

    private ProductsGetVm toProductsGetVm(Page<ProductThumbnailGetVm> page) {
        return new ProductsGetVm(page.getContent(), page.getNumber(), page.getSize(), (int) page.getTotalElements(),
            page.getTotalPages(), page.isLast());
    }

    private ProductThumbnailGetVm toThumbnailGetVm(Product product, Map<Long, String> urls) {
        return new ProductThumbnailGetVm(product.getId(), product.getName(), product.getSlug(),
            MediaService.urlOf(urls, product.getThumbnailMediaId()), product.getPrice());
    }

    private ProductThumbnailVm toThumbnailVm(Product product, Map<Long, String> urls) {
        return new ProductThumbnailVm(product.getId(), product.getName(), product.getSlug(),
            MediaService.urlOf(urls, product.getThumbnailMediaId()));
    }

    /** Thumbnail URLs of a page of products with one media-service call (decisions.md 3b-10). */
    private Map<Long, String> thumbnailUrls(List<Product> products) {
        return mediaService.getMediaUrls(products.stream().map(Product::getThumbnailMediaId).toList());
    }
}
