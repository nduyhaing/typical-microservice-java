package com.shopapp.product.controller;

import com.shopapp.product.service.ProductOptionService;
import com.shopapp.product.viewmodel.productoption.ProductOptionGetVm;
import com.shopapp.product.viewmodel.productoption.ProductOptionPostVm;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Back office product options (YAS paths; role ADMIN via SecurityConfig). */
@RestController
@RequiredArgsConstructor
public class ProductOptionController {

    private final ProductOptionService productOptionService;

    @GetMapping("/backoffice/product-options")
    public ResponseEntity<List<ProductOptionGetVm>> listProductOption() {
        return ResponseEntity.ok(productOptionService.getProductOptions());
    }

    @PostMapping("/backoffice/product-options")
    public ResponseEntity<ProductOptionGetVm> createProductOption(
        @Valid @RequestBody ProductOptionPostVm productOptionPostVm, UriComponentsBuilder uriComponentsBuilder) {
        ProductOptionGetVm created = productOptionService.create(productOptionPostVm);
        return ResponseEntity.created(uriComponentsBuilder.replacePath("/product-options/{id}")
                .buildAndExpand(created.id()).toUri())
            .body(created);
    }
}
