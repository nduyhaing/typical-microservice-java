package com.shopapp.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.support.AbstractStorefrontIT;
import org.junit.jupiter.api.Test;

class CategoryControllerIT extends AbstractStorefrontIT {

    @Test
    void listCategories_returnsAll_anonymous() throws Exception {
        fixtures.category("Phones", "phones");
        fixtures.category("Laptops", "laptops");

        mockMvc.perform(get("/storefront/categories"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.slug == 'phones')].name").value("Phones"))
            .andExpect(jsonPath("$[0].parentId").value(-1));
    }

    @Test
    void listCategories_filtersByName() throws Exception {
        fixtures.category("Phones", "phones");
        fixtures.category("Laptops", "laptops");

        mockMvc.perform(get("/storefront/categories").param("categoryName", "pho"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].slug").value("phones"));
    }

    @Test
    void nonStorefrontPath_requiresAuth() throws Exception {
        mockMvc.perform(get("/backoffice/categories")).andExpect(status().isUnauthorized());
    }
}
