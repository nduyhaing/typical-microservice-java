package com.shopapp.product.controller;

import com.shopapp.product.service.CategoryService;
import com.shopapp.product.viewmodel.category.CategoryGetVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping("/storefront/categories")
    public ResponseEntity<List<CategoryGetVm>> listCategories(
        @RequestParam(required = false, defaultValue = "") String categoryName) {
        return ResponseEntity.ok(categoryService.getCategories(categoryName));
    }
}
