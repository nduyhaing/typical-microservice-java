package com.shopapp.search.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.search.model.Product;
import com.shopapp.search.service.ProductSyncService;
import com.shopapp.search.support.AbstractSearchIT;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProductSearchIT extends AbstractSearchIT {

    @Autowired
    private ProductSyncService syncService;

    @BeforeEach
    void catalog() {
        PRODUCT.product(1, "Galaxy S24", 799, "Samsung", List.of("Phones"), List.of("Color", "Storage"), at(1));
        PRODUCT.product(2, "Galaxy A55", 449, "Samsung", List.of("Phones"), List.of("Color"), at(1));
        PRODUCT.product(3, "iPhone 16", 999, "Apple", List.of("Phones"), List.of("Storage"), at(1));
        PRODUCT.product(4, "MacBook Air", 1299, "Apple", List.of("Laptops"), List.of(), at(1));
        PRODUCT.product(5, "USB-C Cable", 9.9, null, List.of("Accessories"), List.of(), at(1));
        for (long id = 1; id <= 5; id++) {
            syncService.sync(id);
        }
        // a hidden document that slipped into the index must never be returned
        operations.save(Product.builder().id(99L).version(1L).name("Galaxy Secret").price(1.0).brand("Samsung")
            .categories(List.of("Phones")).attributes(List.of()).isPublished(false).isVisibleIndividually(true)
            .build());
        operations.save(Product.builder().id(98L).version(1L).name("Galaxy Variation").price(1.0).brand("Samsung")
            .categories(List.of("Phones")).attributes(List.of()).isPublished(true).isVisibleIndividually(false)
            .build());
        refresh();
    }

    @Test
    void keyword_matchesNameBrandAndCategory_andNeverHiddenProducts() throws Exception {
        mockMvc.perform(get("/storefront/catalog-search").param("keyword", "galaxy"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products[*].name").value(containsInAnyOrder("Galaxy S24", "Galaxy A55")))
            .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/storefront/catalog-search").param("keyword", "Laptops"))
            .andExpect(jsonPath("$.products[*].name").value(contains("MacBook Air")));
        mockMvc.perform(get("/storefront/catalog-search").param("keyword", "secret"))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void noKeyword_returnsEveryVisibleProduct_withFacets() throws Exception {
        mockMvc.perform(get("/storefront/catalog-search"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(5))
            .andExpect(jsonPath("$.products[*].id").value(not(hasItem(99))))
            .andExpect(jsonPath("$.products[*].id").value(not(hasItem(98))))
            .andExpect(jsonPath("$.aggregations.brands.Samsung").value(2))
            .andExpect(jsonPath("$.aggregations.brands.Apple").value(2))
            .andExpect(jsonPath("$.aggregations.categories.Phones").value(3))
            .andExpect(jsonPath("$.aggregations.attributes.Storage").value(2));
    }

    @Test
    void brandCategoryAndPriceFilters() throws Exception {
        mockMvc.perform(get("/storefront/catalog-search").param("brand", "apple"))
            .andExpect(jsonPath("$.products[*].name").value(containsInAnyOrder("iPhone 16", "MacBook Air")));
        mockMvc.perform(get("/storefront/catalog-search").param("brand", "Apple,Samsung").param("category", "Phones"))
            .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(get("/storefront/catalog-search").param("attribute", "Storage"))
            .andExpect(jsonPath("$.products[*].name").value(containsInAnyOrder("Galaxy S24", "iPhone 16")));
        mockMvc.perform(get("/storefront/catalog-search").param("minPrice", "400").param("maxPrice", "800"))
            .andExpect(jsonPath("$.products[*].name").value(containsInAnyOrder("Galaxy S24", "Galaxy A55")));
    }

    @Test
    void filterValuesAreLiteral_notQuerySyntax() throws Exception {
        mockMvc.perform(get("/storefront/catalog-search").param("brand", "Samsung\" OR \"1\"=\"1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/storefront/catalog-search").param("keyword", "{\"match_all\":{}}"))
            .andExpect(status().isOk());
    }

    @Test
    void sortByPrice() throws Exception {
        mockMvc.perform(get("/storefront/catalog-search").param("sortType", "PRICE_ASC"))
            .andExpect(jsonPath("$.products[*].id").value(contains(5, 2, 1, 3, 4)));
        mockMvc.perform(get("/storefront/catalog-search").param("sortType", "PRICE_DESC"))
            .andExpect(jsonPath("$.products[*].id").value(contains(4, 3, 1, 2, 5)));
    }

    @Test
    void paging() throws Exception {
        mockMvc.perform(get("/storefront/catalog-search").param("sortType", "PRICE_ASC")
                .param("page", "1").param("size", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products[*].id").value(contains(1, 3)))
            .andExpect(jsonPath("$.pageNo").value(1))
            .andExpect(jsonPath("$.pageSize").value(2))
            .andExpect(jsonPath("$.totalPages").value(3))
            .andExpect(jsonPath("$.isLast").value(false));
    }

    @Test
    void boundsAreEnforced() throws Exception {
        mockMvc.perform(get("/storefront/catalog-search").param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/catalog-search").param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/catalog-search").param("page", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/catalog-search").param("page", "100").param("size", "100"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/catalog-search").param("minPrice", "-1")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/catalog-search").param("keyword", "x".repeat(101)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/catalog-search").param("sortType", "NAME"))
            .andExpect(status().isBadRequest());
        String brands = String.join(",", IntStream.range(0, 21).mapToObj(i -> "b" + i).toList());
        mockMvc.perform(get("/storefront/catalog-search").param("brand", brands))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/search_suggest").param("keyword", "x".repeat(101)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void suggest_returnsVisibleNamesStartingWithTheWords() throws Exception {
        mockMvc.perform(get("/storefront/search_suggest").param("keyword", "gal"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productNames[*].name").value(containsInAnyOrder("Galaxy S24", "Galaxy A55")));
        mockMvc.perform(get("/storefront/search_suggest").param("keyword", "  "))
            .andExpect(jsonPath("$.productNames").isEmpty());
    }

    @Test
    void reindex_isAdminOnly_andReReadsEveryProduct() throws Exception {
        // no bearer token: refused (CSRF filter, as for every POST without a token in this app)
        mockMvc.perform(post("/backoffice/reindex")).andExpect(status().isForbidden());
        mockMvc.perform(post("/backoffice/reindex").with(customer())).andExpect(status().isForbidden());

        PRODUCT.product(2, "Galaxy A55 5G", 449, "Samsung Electronics", List.of("Phones"), List.of(), at(1));
        mockMvc.perform(post("/backoffice/reindex").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indexed").value(5));

        refresh();
        mockMvc.perform(get("/storefront/catalog-search").param("brand", "Samsung Electronics"))
            .andExpect(jsonPath("$.products[*].name").value(contains("Galaxy A55 5G")));
    }
}
