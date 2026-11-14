package com.shopapp.product.controller;

import com.shopapp.product.service.ProductService;
import com.shopapp.product.viewmodel.product.ProductFeatureGetVm;
import com.shopapp.product.viewmodel.product.ProductListGetFromCategoryVm;
import com.shopapp.product.viewmodel.product.ProductThumbnailVm;
import com.shopapp.product.viewmodel.product.ProductsGetVm;
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

    private final ProductService productService;

    @GetMapping("/storefront/products/featured")
    public ResponseEntity<ProductFeatureGetVm> getFeaturedProducts(
        @RequestParam(value = "pageNo", defaultValue = "0", required = false) @Min(0) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "10", required = false) @Min(1) int pageSize) {
        return ResponseEntity.ok(productService.getListFeaturedProducts(pageNo, pageSize));
    }

    @GetMapping("/storefront/products")
    public ResponseEntity<ProductsGetVm> getProductsByMultiQuery(
        @RequestParam(value = "pageNo", defaultValue = "0", required = false) @Min(0) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "5", required = false) @Min(1) int pageSize,
        @RequestParam(value = "productName", defaultValue = "", required = false) String productName,
        @RequestParam(value = "categorySlug", defaultValue = "", required = false) String categorySlug,
        @RequestParam(value = "startPrice", required = false) Double startPrice,
        @RequestParam(value = "endPrice", required = false) Double endPrice) {
        return ResponseEntity.ok(productService.getProductsByMultiQuery(pageNo, pageSize, productName, categorySlug,
            startPrice, endPrice));
    }

    @GetMapping("/storefront/category/{categorySlug}/products")
    public ResponseEntity<ProductListGetFromCategoryVm> getProductsByCategory(
        @RequestParam(value = "pageNo", defaultValue = "0", required = false) @Min(0) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "2", required = false) @Min(1) int pageSize,
        @PathVariable String categorySlug) {
        return ResponseEntity.ok(productService.getProductsFromCategory(pageNo, pageSize, categorySlug));
    }

    @GetMapping("/storefront/brand/{brandSlug}/products")
    public ResponseEntity<List<ProductThumbnailVm>> getProductsByBrand(@PathVariable String brandSlug) {
        return ResponseEntity.ok(productService.getProductsByBrand(brandSlug));
    }

    @GetMapping("/storefront/products/related-products/{id}")
    public ResponseEntity<ProductsGetVm> getRelatedProductsStorefront(
        @PathVariable Long id,
        @RequestParam(value = "pageNo", defaultValue = "0", required = false) @Min(0) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "5", required = false) @Min(1) int pageSize) {
        return ResponseEntity.ok(productService.getRelatedProductsStorefront(id, pageNo, pageSize));
    }
}
