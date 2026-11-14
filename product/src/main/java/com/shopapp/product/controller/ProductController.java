package com.shopapp.product.controller;

import com.shopapp.product.service.ProductDetailService;
import com.shopapp.product.service.ProductService;
import com.shopapp.product.viewmodel.product.ProductDetailGetVm;
import com.shopapp.product.viewmodel.product.ProductFeatureGetVm;
import com.shopapp.product.viewmodel.product.ProductListGetFromCategoryVm;
import com.shopapp.product.viewmodel.product.ProductThumbnailVm;
import com.shopapp.product.viewmodel.product.ProductVariationGetVm;
import com.shopapp.product.viewmodel.product.ProductsGetVm;
import com.shopapp.product.viewmodel.productoption.ProductOptionCombinationGetVm;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Storefront product endpoints (YAS paths and defaults). Paging values are validated so bad input is a 400,
 * not a 500 from {@code PageRequest.of}.
 */
@RestController
@RequiredArgsConstructor
public class ProductController {

    /** Keeps pageNo * pageSize within an int offset; larger values are a 400, not a 500 (decisions.md 2a-10). */
    static final long MAX_PAGE_NO = 10_000_000;
    /** Bounds the work one request can trigger (one media lookup per product once media exists). */
    static final long MAX_PAGE_SIZE = 100;

    private final ProductService productService;
    private final ProductDetailService productDetailService;

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

    @GetMapping("/storefront/product-variations/{id}")
    public ResponseEntity<List<ProductVariationGetVm>> getProductVariationsByParentId(@PathVariable Long id) {
        return ResponseEntity.ok(productDetailService.getProductVariationsByParentId(id));
    }

    @GetMapping("/storefront/product-option-combinations/{productId}/values")
    public ResponseEntity<List<ProductOptionCombinationGetVm>> listProductOptionValueOfProduct(
        @PathVariable("productId") Long productId) {
        return ResponseEntity.ok(productDetailService.getOptionValues(productId));
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
}
