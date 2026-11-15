package com.shopapp.promotion.controller;

import com.shopapp.commonlibrary.constants.PageableConstant;
import com.shopapp.promotion.service.PromotionService;
import com.shopapp.promotion.utils.Constants;
import com.shopapp.promotion.viewmodel.PromotionApplyUsageVm;
import com.shopapp.promotion.viewmodel.PromotionDetailVm;
import com.shopapp.promotion.viewmodel.PromotionListVm;
import com.shopapp.promotion.viewmodel.PromotionPostVm;
import com.shopapp.promotion.viewmodel.PromotionPutVm;
import com.shopapp.promotion.viewmodel.PromotionUsageResultVm;
import com.shopapp.promotion.viewmodel.PromotionVerifyResultDto;
import com.shopapp.promotion.viewmodel.PromotionVerifyVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    /** YAS list with filters; paging bounded (YAS: unbounded, default size 5). Newest first. */
    @GetMapping("/backoffice/promotions")
    public ResponseEntity<PromotionListVm> listPromotions(
        @RequestParam(value = "pageNo", defaultValue = PageableConstant.DEFAULT_PAGE_NUMBER, required = false)
        @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = PageableConstant.DEFAULT_PAGE_SIZE, required = false)
        @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize,
        @RequestParam(value = "promotionName", defaultValue = "", required = false)
        @Size(max = Constants.MAX_FILTER_LENGTH) String promotionName,
        @RequestParam(value = "couponCode", defaultValue = "", required = false)
        @Size(max = Constants.MAX_FILTER_LENGTH) String couponCode,
        @RequestParam(value = "startDate", required = false) Instant startDate,
        @RequestParam(value = "endDate", required = false) Instant endDate) {
        return ResponseEntity.ok(promotionService.getPromotions(pageNo, pageSize, promotionName, couponCode,
            startDate, endDate));
    }

    @GetMapping("/backoffice/promotions/{promotionId}")
    public ResponseEntity<PromotionDetailVm> getPromotion(@PathVariable("promotionId") Long promotionId) {
        return ResponseEntity.ok(promotionService.getPromotion(promotionId));
    }

    @PostMapping("/backoffice/promotions")
    public ResponseEntity<PromotionDetailVm> createPromotion(@Valid @RequestBody PromotionPostVm promotionPostVm) {
        return new ResponseEntity<>(promotionService.createPromotion(promotionPostVm), HttpStatus.CREATED);
    }

    /** YAS: the id is in the body. */
    @PutMapping("/backoffice/promotions")
    public ResponseEntity<PromotionDetailVm> updatePromotion(@Valid @RequestBody PromotionPutVm promotionPutVm) {
        return ResponseEntity.ok(promotionService.updatePromotion(promotionPutVm));
    }

    @DeleteMapping("/backoffice/promotions/{promotionId}")
    public ResponseEntity<Void> deletePromotion(@PathVariable("promotionId") Long promotionId) {
        promotionService.deletePromotion(promotionId);
        return ResponseEntity.noContent().build();
    }

    /** Any authenticated user (the order service relays the customer's token). */
    @PostMapping("/storefront/promotions/verify")
    public ResponseEntity<PromotionVerifyResultDto> verifyPromotion(@Valid @RequestBody PromotionVerifyVm vm) {
        return ResponseEntity.ok(promotionService.verifyPromotion(vm));
    }

    /**
     * Takes one use of the coupon for the caller; {@code reference} is the caller's idempotency key (one per order).
     * Replaces YAS updateUsage (decisions.md 4-46).
     */
    @PostMapping("/storefront/promotions/apply-usage")
    public ResponseEntity<PromotionUsageResultVm> applyUsage(@RequestParam("reference") UUID reference,
                                                             @Valid @RequestBody PromotionApplyUsageVm vm) {
        return ResponseEntity.ok(promotionService.applyUsage(reference, vm));
    }

    /** Gives back the caller's use taken under {@code reference}; idempotent, other users' references are ignored. */
    @PostMapping("/storefront/promotions/release-usage")
    public ResponseEntity<Void> releaseUsage(@RequestParam("reference") UUID reference) {
        promotionService.releaseUsage(reference);
        return ResponseEntity.noContent().build();
    }
}
