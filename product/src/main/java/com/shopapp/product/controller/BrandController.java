package com.shopapp.product.controller;

import com.shopapp.commonlibrary.constants.PageableConstant;
import com.shopapp.product.service.BrandService;
import com.shopapp.product.viewmodel.brand.BrandListGetVm;
import com.shopapp.product.viewmodel.brand.BrandVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping("/storefront/brands")
    public ResponseEntity<List<BrandVm>> listBrands(
        @RequestParam(required = false, defaultValue = "") String brandName) {
        return ResponseEntity.ok(brandService.getBrands(brandName));
    }

    @GetMapping("/storefront/brands/paging")
    public ResponseEntity<BrandListGetVm> getPageableBrands(
        @RequestParam(value = "pageNo", defaultValue = PageableConstant.DEFAULT_PAGE_NUMBER, required = false)
        int pageNo,
        @RequestParam(value = "pageSize", defaultValue = PageableConstant.DEFAULT_PAGE_SIZE, required = false)
        int pageSize) {
        return ResponseEntity.ok(brandService.getBrands(pageNo, pageSize));
    }
}
