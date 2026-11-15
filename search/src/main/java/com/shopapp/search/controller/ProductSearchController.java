package com.shopapp.search.controller;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.search.model.ProductCriteria;
import com.shopapp.search.model.SortType;
import com.shopapp.search.service.ProductSearchService;
import com.shopapp.search.viewmodel.ProductListGetVm;
import com.shopapp.search.viewmodel.ProductNameListVm;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public search endpoints (YAS paths and parameters). Every input is bounded: keyword ≤ 100 characters, page size
 * 1..100, page × size within Elasticsearch's 10 000 result window, at most 20 comma-separated values of ≤ 100
 * characters per brand/category/attribute filter, prices 0..1 000 000 000 (decisions.md 4-14).
 */
@RestController
@RequiredArgsConstructor
public class ProductSearchController {

    static final int MAX_KEYWORD = 100;
    static final int MAX_PAGE_SIZE = 100;
    static final int MAX_RESULT_WINDOW = 10_000;
    static final int MAX_FILTER_VALUES = 20;
    static final int MAX_FILTER_VALUE_LENGTH = 100;
    static final String MAX_PRICE = "1000000000";

    private final ProductSearchService searchService;

    @GetMapping("/storefront/catalog-search")
    public ResponseEntity<ProductListGetVm> findProductAdvance(
        @RequestParam(defaultValue = "") @Size(max = MAX_KEYWORD) String keyword,
        @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_RESULT_WINDOW) int page,
        @RequestParam(defaultValue = "12") @Min(1) @Max(MAX_PAGE_SIZE) int size,
        @RequestParam(required = false) @Size(max = 2100) String brand,
        @RequestParam(required = false) @Size(max = 2100) String category,
        @RequestParam(required = false) @Size(max = 2100) String attribute,
        @RequestParam(required = false) @DecimalMin("0") @DecimalMax(MAX_PRICE) Double minPrice,
        @RequestParam(required = false) @DecimalMin("0") @DecimalMax(MAX_PRICE) Double maxPrice,
        @RequestParam(defaultValue = "DEFAULT") SortType sortType) {
        if ((long) (page + 1) * size > MAX_RESULT_WINDOW) {
            throw new BadRequestException("page * size must stay within " + MAX_RESULT_WINDOW + " results");
        }
        return ResponseEntity.ok(searchService.search(new ProductCriteria(keyword.trim(), page, size,
            values("brand", brand), values("category", category), values("attribute", attribute),
            minPrice, maxPrice, sortType)));
    }

    @GetMapping("/storefront/search_suggest")
    public ResponseEntity<ProductNameListVm> productSearchAutoComplete(
        @RequestParam @Size(max = MAX_KEYWORD) String keyword) {
        return ResponseEntity.ok(searchService.suggest(keyword.trim()));
    }

    private static List<String> values(String name, String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return List.of();
        }
        List<String> values = Arrays.stream(commaSeparated.split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .distinct()
            .toList();
        if (values.size() > MAX_FILTER_VALUES
            || values.stream().anyMatch(value -> value.length() > MAX_FILTER_VALUE_LENGTH)) {
            throw new BadRequestException(name + " accepts at most " + MAX_FILTER_VALUES + " values of at most "
                + MAX_FILTER_VALUE_LENGTH + " characters");
        }
        return values;
    }
}
