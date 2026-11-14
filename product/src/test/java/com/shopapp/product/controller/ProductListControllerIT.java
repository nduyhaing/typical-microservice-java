package com.shopapp.product.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.support.AbstractStorefrontIT;
import org.junit.jupiter.api.Test;

class ProductListControllerIT extends AbstractStorefrontIT {

    private Product featured(String name, String slug, double price) {
        Product product = fixtures.product(name, slug, price);
        product.setFeatured(true);
        return fixtures.save(product);
    }

    @Test
    void featured_returnsFeaturedPublishedVisibleOnly() throws Exception {
        featured("A", "a", 100);
        fixtures.product("B", "b", 100);
        Product unpublished = featured("C", "c", 100);
        unpublished.setPublished(false);
        fixtures.save(unpublished);

        mockMvc.perform(get("/storefront/products/featured"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productList[*].name").value(containsInAnyOrder("A")))
            .andExpect(jsonPath("$.productList[0].thumbnailUrl").value(""))
            .andExpect(jsonPath("$.productList[0].price").value(100.0))
            .andExpect(jsonPath("$.totalPage").value(1));
    }

    @Test
    void featured_pagePastEnd_returnsEmptyList() throws Exception {
        featured("A", "a", 100);

        mockMvc.perform(get("/storefront/products/featured").param("pageNo", "99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productList").isEmpty());
    }

    @Test
    void products_negativePageSize_returns400() throws Exception {
        mockMvc.perform(get("/storefront/products").param("pageSize", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400 BAD_REQUEST"));
    }

    @Test
    void products_filterByCategorySlugAndPrice() throws Exception {
        Category phones = fixtures.category("Phones", "phones");
        Category laptops = fixtures.category("Laptops", "laptops");
        Product cheapPhone = fixtures.product("Cheap Phone", "cheap-phone", 50);
        Product midPhone = fixtures.product("Mid Phone", "mid-phone", 300);
        Product laptop = fixtures.product("Laptop", "laptop", 300);
        fixtures.inCategory(cheapPhone, phones);
        fixtures.inCategory(midPhone, phones);
        fixtures.inCategory(laptop, laptops);

        mockMvc.perform(get("/storefront/products")
                .param("categorySlug", "phones").param("startPrice", "100").param("endPrice", "500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productContent[*].slug").value(containsInAnyOrder("mid-phone")))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void storefrontLists_excludeUnpublishedAndInvisible() throws Exception {
        Category phones = fixtures.category("Phones", "phones");
        Brand samsung = fixtures.brand("Samsung", "samsung");
        Product parent = fixtures.ofBrand(fixtures.product("Galaxy", "galaxy", 799), samsung);
        Product variation = fixtures.ofBrand(fixtures.variation(parent, "Galaxy Violet", "galaxy-violet", 819), samsung);
        Product draft = fixtures.product("Draft", "draft", 10);
        draft.setPublished(false);
        fixtures.ofBrand(fixtures.save(draft), samsung);
        fixtures.inCategory(parent, phones);
        fixtures.inCategory(variation, phones);
        fixtures.inCategory(draft, phones);

        mockMvc.perform(get("/storefront/products"))
            .andExpect(jsonPath("$.productContent[*].slug").value(containsInAnyOrder("galaxy")));
        mockMvc.perform(get("/storefront/category/phones/products").param("pageSize", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productContent[*].slug").value(containsInAnyOrder("galaxy")))
            .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/storefront/brand/samsung/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].slug").value(containsInAnyOrder("galaxy")));
    }

    @Test
    void categoryProducts_unknownSlug_returns404() throws Exception {
        mockMvc.perform(get("/storefront/category/nope/products"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404 NOT_FOUND"));
    }

    @Test
    void brandProducts_unknownSlug_returns404() throws Exception {
        mockMvc.perform(get("/storefront/brand/nope/products")).andExpect(status().isNotFound());
    }

    @Test
    void relatedProducts_returnsPublishedRelated() throws Exception {
        Product a = fixtures.product("A", "a", 1);
        Product b = fixtures.product("B", "b", 2);
        Product c = fixtures.product("C", "c", 3);
        Product hidden = fixtures.product("Hidden", "hidden", 4);
        hidden.setPublished(false);
        fixtures.save(hidden);
        fixtures.related(a, b);
        fixtures.related(a, c);
        fixtures.related(a, hidden);

        mockMvc.perform(get("/storefront/products/related-products/{id}", a.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productContent[*].slug").value(containsInAnyOrder("b", "c")))
            .andExpect(jsonPath("$.productContent[*].slug").value(not(hasItem("hidden"))));
    }

    @Test
    void products_hugePageNo_returns400() throws Exception {
        mockMvc.perform(get("/storefront/products").param("pageNo", "2147483647"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/products").param("pageNo", "500000000"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void products_hugePageSize_returns400() throws Exception {
        mockMvc.perform(get("/storefront/products").param("pageSize", "100000000"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void products_productInTwoCategories_listedOnce() throws Exception {
        Category phones = fixtures.category("Phones", "phones");
        Category deals = fixtures.category("Deals", "deals");
        Product galaxy = fixtures.product("Galaxy", "galaxy", 799);
        fixtures.inCategory(galaxy, phones);
        fixtures.inCategory(galaxy, deals);

        // page size 1 forces Spring Data to run the count query, where duplicates would show
        mockMvc.perform(get("/storefront/products").param("pageSize", "1"))
            .andExpect(jsonPath("$.productContent.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void relatedProducts_excludeVariations() throws Exception {
        Product a = fixtures.product("A", "a", 1);
        Product b = fixtures.product("B", "b", 2);
        Product variation = fixtures.variation(b, "B Red", "b-red", 2);
        fixtures.related(a, b);
        fixtures.related(a, variation);

        mockMvc.perform(get("/storefront/products/related-products/{id}", a.getId()))
            .andExpect(jsonPath("$.productContent[*].slug").value(containsInAnyOrder("b")))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void listFeatured_returnsPublishedRequestedProducts() throws Exception {
        Product a = fixtures.product("A", "a", 10);
        Product b = fixtures.product("B", "b", 20);
        Product hidden = fixtures.product("Hidden", "hidden", 30);
        hidden.setPublished(false);
        fixtures.save(hidden);

        mockMvc.perform(get("/storefront/products/list-featured")
                .param("productId", String.valueOf(a.getId()), String.valueOf(hidden.getId()), "999999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].slug").value(containsInAnyOrder("a")))
            .andExpect(jsonPath("$[0].price").value(10.0));
        mockMvc.perform(get("/storefront/products/list-featured").param("productId", String.valueOf(b.getId())))
            .andExpect(jsonPath("$[0].name").value("B"));
    }
}
