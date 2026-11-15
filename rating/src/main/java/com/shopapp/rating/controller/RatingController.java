package com.shopapp.rating.controller;

import com.shopapp.rating.service.RatingService;
import com.shopapp.rating.utils.Constants;
import com.shopapp.rating.viewmodel.RatingListVm;
import com.shopapp.rating.viewmodel.RatingPostVm;
import com.shopapp.rating.viewmodel.RatingVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** YAS RatingController paths and view models; bounded paging and filters (decisions.md 4-34). */
@RestController
@RequiredArgsConstructor
public class RatingController {

    private final RatingService ratingService;

    @GetMapping("/backoffice/ratings")
    public ResponseEntity<RatingListVm> getRatingListWithFilter(
        @RequestParam(value = "productName", defaultValue = "", required = false)
        @Size(max = Constants.MAX_FILTER_LENGTH) String productName,
        @RequestParam(value = "customerName", defaultValue = "", required = false)
        @Size(max = Constants.MAX_FILTER_LENGTH) String customerName,
        @RequestParam(value = "message", defaultValue = "", required = false)
        @Size(max = Constants.MAX_FILTER_LENGTH) String message,
        @RequestParam(value = "createdFrom", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime createdFrom,
        @RequestParam(value = "createdTo", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime createdTo,
        @RequestParam(value = "pageNo", defaultValue = "0", required = false)
        @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "10", required = false)
        @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(ratingService.getRatingListWithFilter(productName, customerName, message,
            createdFrom, createdTo, pageNo, pageSize));
    }

    @GetMapping("/backoffice/ratings/latest/{count}")
    public ResponseEntity<List<RatingVm>> getLatestRatings(
        @PathVariable @Min(1) @Max(Constants.MAX_LATEST_COUNT) int count) {
        return ResponseEntity.ok(ratingService.getLatestRatings(count));
    }

    /** YAS answers 200 with a status body; 204 here like the other shop_app deletes. */
    @DeleteMapping("/backoffice/ratings/{id}")
    public ResponseEntity<Void> deleteRating(@PathVariable Long id) {
        ratingService.deleteRating(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/storefront/ratings/products/{productId}")
    public ResponseEntity<RatingListVm> getRatingList(
        @PathVariable Long productId,
        @RequestParam(value = "pageNo", defaultValue = "0", required = false)
        @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "5", required = false)
        @Min(1) @Max(Constants.MAX_STOREFRONT_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(ratingService.getRatingListByProductId(productId, pageNo, pageSize));
    }

    /** YAS answers 200; 201 with a Location here. */
    @PostMapping("/storefront/ratings")
    public ResponseEntity<RatingVm> createRating(@Valid @RequestBody RatingPostVm ratingPostVm) {
        RatingVm rating = ratingService.createRating(ratingPostVm);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/storefront/ratings/products/{productId}").buildAndExpand(rating.productId()).toUri())
            .body(rating);
    }

    /** Not in YAS: a customer deletes their own rating (decisions.md 4-33). */
    @DeleteMapping("/storefront/ratings/{id}")
    public ResponseEntity<Void> deleteOwnRating(@PathVariable Long id) {
        ratingService.deleteOwnRating(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/storefront/ratings/product/{productId}/average-star")
    public Double getAverageStarOfProduct(@PathVariable Long productId) {
        return ratingService.calculateAverageStar(productId);
    }
}
