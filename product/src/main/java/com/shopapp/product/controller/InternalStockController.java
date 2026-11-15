package com.shopapp.product.controller;

import com.shopapp.product.service.ProductStockService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service stock endpoints (decisions.md 5-5): realm role SERVICE only (a client-credentials token), refused
 * by nginx and both BFFs.
 */
@Validated
@RestController
@RequiredArgsConstructor
public class InternalStockController {

    private final ProductStockService productStockService;

    /**
     * The order service gives a cancelled order's stock back: the units subtracted under {@code reference}, whoever
     * subtracted them (unlike the caller-bound {@code /backoffice/products/restore-quantity}), at most once.
     */
    @PutMapping("/internal/products/restore-quantity")
    public ResponseEntity<Void> restoreQuantity(
        @RequestParam("reference") @Pattern(regexp = ProductController.REFERENCE_PATTERN) String reference) {
        productStockService.restoreStockQuantityOfAnyCaller(reference);
        return ResponseEntity.noContent().build();
    }
}
