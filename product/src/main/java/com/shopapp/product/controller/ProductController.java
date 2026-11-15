package com.shopapp.product.controller;

import com.shopapp.product.model.enumeration.FilterExistInWhSelection;
import com.shopapp.product.service.ProductAdminService;
import com.shopapp.product.service.ProductDetailService;
import com.shopapp.product.service.ProductService;
import com.shopapp.product.service.ProductStockService;
import com.shopapp.product.viewmodel.product.ProductDetailGetVm;
import com.shopapp.product.viewmodel.product.ProductDetailVm;
import com.shopapp.product.viewmodel.product.ProductEsDetailVm;
import com.shopapp.product.viewmodel.product.ProductFeatureGetVm;
import com.shopapp.product.viewmodel.product.ProductGetCheckoutListVm;
import com.shopapp.product.viewmodel.product.ProductGetDetailVm;
import com.shopapp.product.viewmodel.product.ProductInfoVm;
import com.shopapp.product.viewmodel.product.ProductListGetFromCategoryVm;
import com.shopapp.product.viewmodel.product.ProductListGetVm;
import com.shopapp.product.viewmodel.product.ProductListVm;
import com.shopapp.product.viewmodel.product.ProductPostVm;
import com.shopapp.product.viewmodel.product.ProductPromotionTargetVm;
import com.shopapp.product.viewmodel.product.ProductPutVm;
import com.shopapp.product.viewmodel.product.ProductQuantityDeltaVm;
import com.shopapp.product.viewmodel.product.ProductQuantityPostVm;
import com.shopapp.product.viewmodel.product.ProductQuantityPutVm;
import com.shopapp.product.viewmodel.product.ProductThumbnailGetVm;
import com.shopapp.product.viewmodel.product.ProductThumbnailVm;
import com.shopapp.product.viewmodel.product.ProductVariationGetVm;
import com.shopapp.product.viewmodel.product.ProductsGetVm;
import com.shopapp.product.viewmodel.productoption.ProductOptionCombinationGetVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Storefront and back office product endpoints (YAS paths and defaults). Paging values are validated so bad input
 * is a 400, not a 500 from {@code PageRequest.of}. {@code /backoffice/**} requires role ADMIN (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class ProductController {

    /** Keeps pageNo * pageSize within an int offset; larger values are a 400, not a 500 (decisions.md 2a-10). */
    static final long MAX_PAGE_NO = 10_000_000;
    /** Bounds the work one request can trigger (one media lookup per product once media exists). */
    static final long MAX_PAGE_SIZE = 100;
    /** At most this many items per stock quantity call. */
    static final int MAX_QUANTITY_ITEMS = 100;
    /** A subtract/restore reference: 1..64 letters, digits, '-' or '_' (decisions.md 3c-22). */
    static final String REFERENCE_PATTERN = "^[A-Za-z0-9_-]{1,64}$";

    private final ProductService productService;
    private final ProductDetailService productDetailService;
    private final ProductAdminService productAdminService;
    private final ProductStockService productStockService;

    @GetMapping("/backoffice/products")
    public ResponseEntity<ProductListGetVm> listProducts(
        @RequestParam(value = "pageNo", defaultValue = "0", required = false)
        @Min(0) @Max(MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "5", required = false)
        @Min(1) @Max(MAX_PAGE_SIZE) int pageSize,
        @RequestParam(value = "product-name", defaultValue = "", required = false) String productName,
        @RequestParam(value = "brand-name", defaultValue = "", required = false) String brandName) {
        return ResponseEntity.ok(productAdminService.getProducts(pageNo, pageSize, productName, brandName));
    }

    @GetMapping("/backoffice/products/{productId}")
    public ResponseEntity<ProductDetailVm> getProductById(@PathVariable long productId) {
        return ResponseEntity.ok(productAdminService.getProduct(productId));
    }

    @PostMapping(path = "/backoffice/products", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ProductGetDetailVm> createProduct(@Valid @RequestBody ProductPostVm productPostVm) {
        return new ResponseEntity<>(productAdminService.create(productPostVm), HttpStatus.CREATED);
    }

    @PutMapping(path = "/backoffice/products/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateProduct(@PathVariable long id, @Valid @RequestBody ProductPutVm productPutVm) {
        productAdminService.update(id, productPutVm);
        return ResponseEntity.noContent().build();
    }

    /** YAS soft delete: unpublishes the product (decisions.md 3a-17). */
    @DeleteMapping("/backoffice/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productAdminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/backoffice/product-variations/{id}")
    public ResponseEntity<List<ProductVariationGetVm>> getProductVariationsBackoffice(@PathVariable Long id) {
        return ResponseEntity.ok(productAdminService.getVariations(id));
    }

    @GetMapping("/storefront/products/featured")
    public ResponseEntity<ProductFeatureGetVm> getFeaturedProducts(
        @RequestParam(value = "pageNo", defaultValue = "0", required = false)
        @Min(0) @Max(MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "10", required = false)
        @Min(1) @Max(MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(productService.getListFeaturedProducts(pageNo, pageSize));
    }

    @GetMapping("/storefront/products")
    public ResponseEntity<ProductsGetVm> getProductsByMultiQuery(
        @RequestParam(value = "pageNo", defaultValue = "0", required = false)
        @Min(0) @Max(MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "5", required = false)
        @Min(1) @Max(MAX_PAGE_SIZE) int pageSize,
        @RequestParam(value = "productName", defaultValue = "", required = false) String productName,
        @RequestParam(value = "categorySlug", defaultValue = "", required = false) String categorySlug,
        @RequestParam(value = "startPrice", required = false) Double startPrice,
        @RequestParam(value = "endPrice", required = false) Double endPrice) {
        return ResponseEntity.ok(productService.getProductsByMultiQuery(pageNo, pageSize, productName, categorySlug,
            startPrice, endPrice));
    }

    /** Used by the cart service and the cart page to look up products by id (YAS name kept). */
    @GetMapping("/storefront/products/list-featured")
    public ResponseEntity<List<ProductThumbnailGetVm>> getFeaturedProductsById(
        @RequestParam("productId") List<Long> productIds) {
        return ResponseEntity.ok(productService.getFeaturedProductsById(productIds));
    }

    /** YAS: price lookup for the order service at checkout. Authenticated (not under /storefront). */
    @GetMapping("/products")
    public ResponseEntity<ProductGetCheckoutListVm> getProductCheckoutList(
        @RequestParam(value = "pageNo", defaultValue = "0", required = false)
        @Min(0) @Max(MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "20", required = false)
        @Min(1) @Max(MAX_PAGE_SIZE) int pageSize,
        @RequestParam(value = "ids") @Size(max = 100) List<Long> productIds) {
        return ResponseEntity.ok(productService.getProductCheckoutList(pageNo, pageSize, productIds));
    }

    /**
     * Not in YAS: promotion's one call per coupon check (decisions.md 4-74). Public catalog data (orderable products'
     * parent, brand, category ids and price), like the storefront lists; 1..100 ids.
     */
    @GetMapping("/storefront/products/promotion-targets")
    public ResponseEntity<List<ProductPromotionTargetVm>> getPromotionTargets(
        @RequestParam(value = "ids") @NotEmpty @Size(max = ProductService.MAX_PROMOTION_TARGETS)
        List<Long> productIds) {
        return ResponseEntity.ok(productService.getPromotionTargets(productIds));
    }

    @GetMapping("/storefront/category/{categorySlug}/products")
    public ResponseEntity<ProductListGetFromCategoryVm> getProductsByCategory(
        @RequestParam(value = "pageNo", defaultValue = "0", required = false)
        @Min(0) @Max(MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "2", required = false)
        @Min(1) @Max(MAX_PAGE_SIZE) int pageSize,
        @PathVariable String categorySlug) {
        return ResponseEntity.ok(productService.getProductsFromCategory(pageNo, pageSize, categorySlug));
    }

    @GetMapping("/storefront/brand/{brandSlug}/products")
    public ResponseEntity<List<ProductThumbnailVm>> getProductsByBrand(@PathVariable String brandSlug) {
        return ResponseEntity.ok(productService.getProductsByBrand(brandSlug));
    }

    @GetMapping("/storefront/product/{slug}")
    public ResponseEntity<ProductDetailGetVm> getProductDetail(@PathVariable("slug") String slug) {
        return ResponseEntity.ok(productDetailService.getProductDetail(slug));
    }

    /** YAS: the search service's indexing read; 404 unless published and visible individually (4-10). */
    @GetMapping("/storefront/products-es/{productId}")
    public ResponseEntity<ProductEsDetailVm> getProductEsDetailById(@PathVariable long productId) {
        return ResponseEntity.ok(productDetailService.getProductEsDetail(productId));
    }

    @GetMapping("/storefront/product-variations/{id}")
    public ResponseEntity<List<ProductVariationGetVm>> getProductVariationsByParentId(@PathVariable Long id) {
        return ResponseEntity.ok(productDetailService.getProductVariationsByParentId(id));
    }

    @GetMapping("/storefront/product-option-combinations/{productId}/values")
    public ResponseEntity<List<ProductOptionCombinationGetVm>> listProductOptionValueOfProduct(
        @PathVariable("productId") Long productId) {
        return ResponseEntity.ok(productDetailService.getOptionValues(productId));
    }

    @GetMapping("/backoffice/products/related-products/{id}")
    public ResponseEntity<List<ProductListVm>> getRelatedProductsBackoffice(@PathVariable Long id) {
        return ResponseEntity.ok(productAdminService.getRelatedProducts(id));
    }

    @GetMapping("/storefront/products/related-products/{id}")
    public ResponseEntity<ProductsGetVm> getRelatedProductsStorefront(
        @PathVariable Long id,
        @RequestParam(value = "pageNo", defaultValue = "0", required = false)
        @Min(0) @Max(MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "5", required = false)
        @Min(1) @Max(MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(productService.getRelatedProductsStorefront(id, pageNo, pageSize));
    }

    /** YAS: sets each product's stock quantity. ADMIN; inventory sends deltas to adjust-quantity instead (3c-60). */
    @PutMapping(path = "/backoffice/products/update-quantity", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> updateProductQuantity(
        @Valid @RequestBody @NotEmpty @Size(max = MAX_QUANTITY_ITEMS) List<ProductQuantityPostVm> items) {
        productStockService.updateProductQuantity(items);
        return ResponseEntity.noContent().build();
    }

    /**
     * Inventory's stock adjustment: adds each product's delta (negative removes) to its sellable stock, never below
     * 0, applied once per {@code reference} (decisions.md 3c-60). ADMIN.
     */
    @PutMapping(path = "/backoffice/products/adjust-quantity", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> adjustProductQuantity(
        @Valid @RequestBody @NotEmpty @Size(max = MAX_QUANTITY_ITEMS) List<ProductQuantityDeltaVm> items,
        @RequestParam("reference") @Pattern(regexp = REFERENCE_PATTERN) String reference) {
        productStockService.adjustStockQuantity(items, reference);
        return ResponseEntity.noContent().build();
    }

    /**
     * YAS: the order service takes stock with the customer's token, so any signed-in user may call it; it only
     * decreases, atomically, all or nothing (decisions.md 3c-21). {@code reference} (required) records the units for
     * {@link #restoreProductQuantity(String)}. Only reachable inside the compose network: nginx and the BFFs refuse
     * it (3c-63).
     */
    @PutMapping(path = "/backoffice/products/subtract-quantity", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> subtractProductQuantity(
        @Valid @RequestBody @NotEmpty @Size(max = MAX_QUANTITY_ITEMS) List<ProductQuantityPutVm> items,
        @RequestParam("reference") @Pattern(regexp = REFERENCE_PATTERN) String reference) {
        productStockService.subtractStockQuantity(items, reference);
        return ResponseEntity.noContent().build();
    }

    /** Order compensation: gives back, once, what the same caller took under {@code reference} (3c-22). */
    @PutMapping("/backoffice/products/restore-quantity")
    public ResponseEntity<Void> restoreProductQuantity(
        @RequestParam("reference") @Pattern(regexp = REFERENCE_PATTERN) String reference) {
        productStockService.restoreStockQuantity(reference);
        return ResponseEntity.noContent().build();
    }

    /** YAS: the inventory service's product lookup (search and in / not in a warehouse's product ids). ADMIN. */
    @GetMapping("/backoffice/products/for-warehouse")
    public ResponseEntity<List<ProductInfoVm>> getProductsForWarehouse(
        @RequestParam(value = "name", defaultValue = "", required = false) String name,
        @RequestParam(value = "sku", defaultValue = "", required = false) String sku,
        @RequestParam(value = "productIds", required = false) List<Long> productIds,
        @RequestParam(value = "selection", defaultValue = "ALL", required = false)
        FilterExistInWhSelection selection) {
        return ResponseEntity.ok(productStockService.getProductsForWarehouse(name, sku, productIds, selection));
    }
}
