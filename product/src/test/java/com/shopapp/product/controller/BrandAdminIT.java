package com.shopapp.product.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Product;
import com.shopapp.product.support.AbstractStorefrontIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Back office brand endpoints (YAS paths): ADMIN only, CRUD, duplicate and in-use refusals.
 */
class BrandAdminIT extends AbstractStorefrontIT {

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void createBrand_admin_returns201AndIsListed() throws Exception {
        mockMvc.perform(json(post("/backoffice/brands"), "{\"name\":\"Apple\",\"slug\":\"apple\",\"isPublish\":true}")
                .with(admin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Apple"))
            .andExpect(jsonPath("$.slug").value("apple"))
            .andExpect(jsonPath("$.isPublish").value(true));

        mockMvc.perform(get("/backoffice/brands").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].slug").value("apple"));
        mockMvc.perform(get("/storefront/brands"))
            .andExpect(jsonPath("$[0].slug").value("apple"));
    }

    @Test
    void getBrand_admin_returnsBrand_orNotFound() throws Exception {
        Brand sony = fixtures.brand("Sony", "sony");

        mockMvc.perform(get("/backoffice/brands/{id}", sony.getId()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Sony"));
        mockMvc.perform(get("/backoffice/brands/{id}", 999).with(admin()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Brand 999 is not found"));
    }

    @Test
    void brandsPaging_admin_returnsPage_andRejectsBadPaging() throws Exception {
        fixtures.brand("Apple", "apple");
        fixtures.brand("Sony", "sony");

        mockMvc.perform(get("/backoffice/brands/paging").param("pageNo", "1").param("pageSize", "1").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandContent.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.isLast").value(true));
        mockMvc.perform(get("/backoffice/brands/paging").param("pageNo", "-1").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/brands/paging").param("pageSize", "0").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/brands/paging").param("pageSize", "101").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updateBrand_admin_changesFields() throws Exception {
        Brand sony = fixtures.brand("Sony", "sony");

        mockMvc.perform(json(put("/backoffice/brands/{id}", sony.getId()),
                "{\"name\":\"Sony Group\",\"slug\":\"sony-group\",\"isPublish\":false}").with(admin()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/backoffice/brands/{id}", sony.getId()).with(admin()))
            .andExpect(jsonPath("$.name").value("Sony Group"))
            .andExpect(jsonPath("$.slug").value("sony-group"))
            .andExpect(jsonPath("$.isPublish").value(false));
    }

    @Test
    void updateBrand_keepingOwnNameAndSlug_isAllowed() throws Exception {
        Brand sony = fixtures.brand("Sony", "sony");

        mockMvc.perform(json(put("/backoffice/brands/{id}", sony.getId()),
                "{\"name\":\"Sony\",\"slug\":\"sony\",\"isPublish\":true}").with(admin()))
            .andExpect(status().isNoContent());
    }

    @Test
    void updateBrand_unknown_returns404() throws Exception {
        mockMvc.perform(json(put("/backoffice/brands/{id}", 999),
                "{\"name\":\"X\",\"slug\":\"x\",\"isPublish\":true}").with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createBrand_duplicateName_returns400() throws Exception {
        fixtures.brand("Apple", "apple");

        mockMvc.perform(json(post("/backoffice/brands"), "{\"name\":\"Apple\",\"slug\":\"apple-2\",\"isPublish\":true}")
                .with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Request name Apple is already existed"));
    }

    @Test
    void createOrUpdateBrand_duplicateSlug_returns400() throws Exception {
        fixtures.brand("Apple", "apple");
        Brand sony = fixtures.brand("Sony", "sony");

        mockMvc.perform(json(post("/backoffice/brands"), "{\"name\":\"Apple 2\",\"slug\":\"apple\",\"isPublish\":true}")
                .with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Slug apple is already existed or is duplicated"));
        mockMvc.perform(json(put("/backoffice/brands/{id}", sony.getId()),
                "{\"name\":\"Sony\",\"slug\":\"apple\",\"isPublish\":true}").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createBrand_blankName_returns400() throws Exception {
        mockMvc.perform(json(post("/backoffice/brands"), "{\"name\":\" \",\"slug\":\"x\",\"isPublish\":true}")
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteBrand_unused_returns204() throws Exception {
        Brand sony = fixtures.brand("Sony", "sony");

        mockMvc.perform(delete("/backoffice/brands/{id}", sony.getId()).with(admin()))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/brands/{id}", sony.getId()).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteBrand_inUse_returns400() throws Exception {
        Brand apple = fixtures.brand("Apple", "apple");
        Product iphone = fixtures.product("iPhone", "iphone", 999);
        fixtures.ofBrand(iphone, apple);

        mockMvc.perform(delete("/backoffice/brands/{id}", apple.getId()).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Please make sure this brand don't contains any product"));
    }

    @Test
    void deleteBrand_unknown_returns404() throws Exception {
        mockMvc.perform(delete("/backoffice/brands/{id}", 999).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void customer_isForbidden() throws Exception {
        Brand sony = fixtures.brand("Sony", "sony");

        mockMvc.perform(get("/backoffice/brands").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(json(post("/backoffice/brands"), "{\"name\":\"A\",\"slug\":\"a\",\"isPublish\":true}")
                .with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/backoffice/brands/{id}", sony.getId()).with(customer()))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/backoffice/brands")).andExpect(status().isUnauthorized());
        // Without a bearer header Spring's CSRF check refuses the write first (403); either way nothing is created.
        mockMvc.perform(json(post("/backoffice/brands"), "{\"name\":\"A\",\"slug\":\"a\",\"isPublish\":true}"))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(get("/backoffice/brands").with(admin()))
            .andExpect(jsonPath("$.length()").value(0));
    }
}
