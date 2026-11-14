package com.shopapp.product.controller;

import com.shopapp.product.service.CategoryService;
import com.shopapp.product.viewmodel.category.CategoryGetDetailVm;
import com.shopapp.product.viewmodel.category.CategoryGetVm;
import com.shopapp.product.viewmodel.category.CategoryPostVm;
import jakarta.validation.Valid;
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
 * Category endpoints (YAS paths). {@code /backoffice/**} requires role ADMIN (SecurityConfig).
 */
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping({"/backoffice/categories", "/storefront/categories"})
    public ResponseEntity<List<CategoryGetVm>> listCategories(
        @RequestParam(required = false, defaultValue = "") String categoryName) {
        return ResponseEntity.ok(categoryService.getCategories(categoryName));
    }

    @GetMapping("/backoffice/categories/{id}")
    public ResponseEntity<CategoryGetDetailVm> getCategory(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PostMapping("/backoffice/categories")
    public ResponseEntity<CategoryGetDetailVm> createCategory(@Valid @RequestBody CategoryPostVm categoryPostVm,
                                                              UriComponentsBuilder uriComponentsBuilder) {
        CategoryGetDetailVm created = categoryService.create(categoryPostVm);
        return ResponseEntity.created(uriComponentsBuilder.replacePath("/categories/{id}")
                .buildAndExpand(created.id()).toUri())
            .body(created);
    }

    @PutMapping("/backoffice/categories/{id}")
    public ResponseEntity<Void> updateCategory(@PathVariable Long id,
                                               @Valid @RequestBody CategoryPostVm categoryPostVm) {
        categoryService.update(categoryPostVm, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/backoffice/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
