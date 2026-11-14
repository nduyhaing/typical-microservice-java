package com.shopapp.product.controller;

import com.shopapp.commonlibrary.constants.PageableConstant;
import com.shopapp.product.model.Brand;
import com.shopapp.product.service.BrandService;
import com.shopapp.product.viewmodel.brand.BrandListGetVm;
import com.shopapp.product.viewmodel.brand.BrandPostVm;
import com.shopapp.product.viewmodel.brand.BrandVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Brand endpoints (YAS paths). {@code /backoffice/**} requires role ADMIN (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping({"/backoffice/brands", "/storefront/brands"})
    public ResponseEntity<List<BrandVm>> listBrands(
        @RequestParam(required = false, defaultValue = "") String brandName) {
        return ResponseEntity.ok(brandService.getBrands(brandName));
    }

    /** Paging bounded as the product lists (decisions.md 2a-10, 3a-10). */
    @GetMapping({"/backoffice/brands/paging", "/storefront/brands/paging"})
    public ResponseEntity<BrandListGetVm> getPageableBrands(
        @RequestParam(value = "pageNo", defaultValue = PageableConstant.DEFAULT_PAGE_NUMBER, required = false)
        @Min(0) @Max(ProductController.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = PageableConstant.DEFAULT_PAGE_SIZE, required = false)
        @Min(1) @Max(ProductController.MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(brandService.getBrands(pageNo, pageSize));
    }

    @GetMapping("/backoffice/brands/{id}")
    public ResponseEntity<BrandVm> getBrand(@PathVariable Long id) {
        return ResponseEntity.ok(brandService.getBrand(id));
    }

    @PostMapping("/backoffice/brands")
    public ResponseEntity<BrandVm> createBrand(@Valid @RequestBody BrandPostVm brandPostVm,
                                               UriComponentsBuilder uriComponentsBuilder) {
        Brand brand = brandService.create(brandPostVm);
        return ResponseEntity.created(uriComponentsBuilder.replacePath("/brands/{id}")
                .buildAndExpand(brand.getId()).toUri())
            .body(BrandVm.fromModel(brand));
    }

    @PutMapping("/backoffice/brands/{id}")
    public ResponseEntity<Void> updateBrand(@PathVariable Long id, @Valid @RequestBody BrandPostVm brandPostVm) {
        brandService.update(brandPostVm, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/backoffice/brands/{id}")
    public ResponseEntity<Void> deleteBrand(@PathVariable Long id) {
        brandService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
