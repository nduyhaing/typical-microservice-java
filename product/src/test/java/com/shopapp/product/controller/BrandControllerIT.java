package com.shopapp.product.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.support.AbstractStorefrontIT;
import org.junit.jupiter.api.Test;

class BrandControllerIT extends AbstractStorefrontIT {

    @Test
    void listBrands_returnsAll() throws Exception {
        fixtures.brand("Apple", "apple");
        fixtures.brand("Sony", "sony");

        mockMvc.perform(get("/storefront/brands"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.slug == 'sony')].name").value("Sony"));
    }

    @Test
    void listBrands_filtersByName() throws Exception {
        fixtures.brand("Apple", "apple");
        fixtures.brand("Sony", "sony");

        mockMvc.perform(get("/storefront/brands").param("brandName", "app"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].slug").value("apple"));
    }

    @Test
    void brandsPaging_returnsPage() throws Exception {
        fixtures.brand("Apple", "apple");
        fixtures.brand("Sony", "sony");

        mockMvc.perform(get("/storefront/brands/paging").param("pageNo", "0").param("pageSize", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandContent.length()").value(1))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.totalElements").value(2));
    }
}
