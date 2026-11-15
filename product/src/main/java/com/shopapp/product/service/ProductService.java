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
 * Storefront product listings (ported from YAS ProductService). Each listing reads its page in a read-only
 * transaction and resolves the thumbnail URLs after it, with one media-service call (decisions.md 3b-10, 3b-19).
 */
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final ProductRelatedRepository productRelatedRepository;
    private final MediaUrlResolver mediaUrlResolver;

    public ProductFeatureGetVm getListFeaturedProducts(int pageNo, int pageSize) {
        return mediaUrlResolver.readThenResolve(() -> {
            Page<Product> productPage = productRepository.getFeaturedProduct(PageRequest.of(pageNo, pageSize));
            List<Card> cards = Card.of(productPage.getContent());
            int totalPages = productPage.getTotalPages();
            return new WithMedia<>(Card.mediaIds(cards),
                urls -> new ProductFeatureGetVm(Card.toGetVms(cards, urls), totalPages));
        });
    }

    public ProductsGetVm getProductsByMultiQuery(int pageNo, int pageSize, String productName, String categorySlug,
                                                 Double startPrice, Double endPrice) {
        return mediaUrlResolver.readThenResolve(() -> {
            Page<Product> productPage = productRepository.findByProductNameAndCategorySlugAndPriceBetween(
                productName.trim().toLowerCase(), categorySlug.trim(), startPrice, endPrice,
                PageRequest.of(pageNo, pageSize));
            return productsGetVm(productPage, productPage.getContent());
        });
    }

    public ProductListGetFromCategoryVm getProductsFromCategory(int pageNo, int pageSize, String categorySlug) {
        return mediaUrlResolver.readThenResolve(() -> {
            Category category = categoryRepository.findBySlug(categorySlug)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.CATEGORY_NOT_FOUND, categorySlug));
            Page<Product> page = productCategoryRepository
                .findStorefrontByCategory(category, PageRequest.of(pageNo, pageSize))
                .map(ProductCategory::getProduct);
            List<Card> cards = Card.of(page.getContent());
            return new WithMedia<>(Card.mediaIds(cards), urls -> new ProductListGetFromCategoryVm(
                Card.toVms(cards, urls), page.getNumber(), page.getSize(), (int) page.getTotalElements(),
                page.getTotalPages(), page.isLast()));
        });
    }

    public List<ProductThumbnailVm> getProductsByBrand(String brandSlug) {
        return mediaUrlResolver.readThenResolve(() -> {
            Brand brand = brandRepository.findBySlug(brandSlug)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.BRAND_NOT_FOUND, brandSlug));
            List<Card> cards = Card.of(
                productRepository.findAllByBrandAndIsPublishedTrueAndIsVisibleIndividuallyTrueOrderByIdAsc(brand));
            return new WithMedia<>(Card.mediaIds(cards), urls -> Card.toVms(cards, urls));
        });
    }

    /**
     * Thumbnails (with price) of the requested published products; unknown or hidden ids, and variations of an
     * unpublished parent, are left out (decisions.md 3a-33).
     */
    public List<ProductThumbnailGetVm> getFeaturedProductsById(List<Long> productIds) {
        return mediaUrlResolver.readThenResolve(() -> {
            List<Card> cards = Card.of(productRepository.findStorefrontByIds(productIds));
            return new WithMedia<>(Card.mediaIds(cards), urls -> Card.toGetVms(cards, urls));
        });
    }

    /**
     * YAS GET /products: the order service's price lookup at checkout, orderable products only. It answers without
     * calling the media service: the order service reads only id, name and price, so {@code thumbnailUrl} stays
     * empty and checkout never waits for images (decisions.md 3b-18).
     */
    @Transactional(readOnly = true)
    public ProductGetCheckoutListVm getProductCheckoutList(int pageNo, int pageSize, List<Long> productIds) {
        Page<Product> page = productRepository.findOrderableByIds(productIds, PageRequest.of(pageNo, pageSize));
        return new ProductGetCheckoutListVm(
            page.map(product -> ProductCheckoutListVm.fromModel(product, "")).getContent(),
            page.getNumber(), page.getSize(), (int) page.getTotalElements(), page.getTotalPages(), page.isLast());
    }

    public ProductsGetVm getRelatedProductsStorefront(Long id, int pageNo, int pageSize) {
        return mediaUrlResolver.readThenResolve(() -> {
            Product product = productRepository.findStorefrontById(id)
                .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.PRODUCT_NOT_FOUND, id));
            Page<ProductRelated> relatedPage = productRelatedRepository.findStorefrontRelated(product,
                PageRequest.of(pageNo, pageSize));
            return productsGetVm(relatedPage, relatedPage.stream().map(ProductRelated::getRelatedProduct).toList());
        });
    }

    private static WithMedia<ProductsGetVm> productsGetVm(Page<?> page, List<Product> products) {
        List<Card> cards = Card.of(products);
        int number = page.getNumber();
        int size = page.getSize();
        int totalElements = (int) page.getTotalElements();
        int totalPages = page.getTotalPages();
        boolean last = page.isLast();
        return new WithMedia<>(Card.mediaIds(cards), urls -> new ProductsGetVm(Card.toGetVms(cards, urls), number,
            size, totalElements, totalPages, last));
    }

    /** The plain values of a product card, read inside the transaction; the URL is filled in after it. */
    private record Card(long id, String name, String slug, Long thumbnailMediaId, Double price,
                        boolean stockTrackingEnabled, boolean stockAvailable, Long stockQuantity) {

        static List<Card> of(List<Product> products) {
            return products.stream().map(product -> new Card(product.getId(), product.getName(), product.getSlug(),
                product.getThumbnailMediaId(), product.getPrice(), product.isStockTrackingEnabled(),
                product.stockAvailable(), product.trackedStockQuantity())).toList();
        }

        static List<Long> mediaIds(List<Card> cards) {
            return cards.stream().map(Card::thumbnailMediaId).toList();
        }

        static List<ProductThumbnailGetVm> toGetVms(List<Card> cards, Map<Long, String> urls) {
            return cards.stream().map(card -> new ProductThumbnailGetVm(card.id, card.name, card.slug,
                MediaService.urlOf(urls, card.thumbnailMediaId), card.price, card.stockTrackingEnabled,
                card.stockAvailable, card.stockQuantity)).toList();
        }

        static List<ProductThumbnailVm> toVms(List<Card> cards, Map<Long, String> urls) {
            return cards.stream().map(card -> new ProductThumbnailVm(card.id, card.name, card.slug,
                MediaService.urlOf(urls, card.thumbnailMediaId), card.stockTrackingEnabled, card.stockAvailable,
                card.stockQuantity)).toList();
        }
    }
}
