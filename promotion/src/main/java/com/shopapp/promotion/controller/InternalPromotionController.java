package com.shopapp.promotion.controller;

import com.shopapp.promotion.service.InternalPromotionUsageService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service coupon endpoints (decisions.md 5-5): realm role SERVICE only (a client-credentials token),
 * refused by nginx and both BFFs.
 */
@RestController
@RequiredArgsConstructor
public class InternalPromotionController {

    private final InternalPromotionUsageService usageService;

    /**
     * The order service gives a cancelled order's coupon use back: the use taken under {@code reference}, whoever took
     * it (unlike the caller-bound {@code /storefront/promotions/release-usage}), at most once.
     */
    @PostMapping("/internal/promotions/release-usage")
    public ResponseEntity<Void> releaseUsage(@RequestParam("reference") UUID reference) {
        usageService.releaseUsage(reference);
        return ResponseEntity.noContent().build();
    }
}
