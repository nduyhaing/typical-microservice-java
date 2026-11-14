package com.shopapp.product.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.support.AbstractStorefrontIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Back office category endpoints (YAS paths): ADMIN only, CRUD, parent rules, duplicate and in-use refusals.
 */
class CategoryAdminIT extends AbstractStorefrontIT {

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static String body(String name, String slug, Long parentId) {
        return "{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\",\"description\":\"d\",\"parentId\":"
            + parentId + ",\"metaKeywords\":\"k\",\"metaDescription\":\"m\",\"displayOrder\":2,\"isPublish\":true}";
    }

    @Test
    void createCategory_admin_returns201_andIsListed() throws Exception {
        Category phones = fixtures.category("Phones", "phones");

        mockMvc.perform(json(post("/backoffice/categories"), body("Smartphones", "smartphones", phones.getId()))
                .with(admin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("Smartphones"))
            .andExpect(jsonPath("$.parentId").value(phones.getId()))
            .andExpect(jsonPath("$.displayOrder").value(2))
            .andExpect(jsonPath("$.isPublish").value(true));

        mockMvc.perform(get("/backoffice/categories").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[?(@.slug == 'smartphones')].parentId").value(phones.getId().intValue()));
        mockMvc.perform(get("/storefront/categories").param("categoryName", "smart"))
            .andExpect(jsonPath("$[0].slug").value("smartphones"));
    }

    @Test
    void createCategory_withoutOptionalFields_returns201() throws Exception {
        mockMvc.perform(json(post("/backoffice/categories"), "{\"name\":\"Phones\",\"slug\":\"phones\"}")
                .with(admin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.parentId").value(0));
    }

    @Test
    void createCategory_unknownParent_returns400() throws Exception {
        mockMvc.perform(json(post("/backoffice/categories"), body("Phones", "phones", 999L)).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Parent category 999 is not found"));
    }

    @Test
    void getCategory_admin_returnsDetail_orNotFound() throws Exception {
        Category phones = fixtures.category("Phones", "phones");

        mockMvc.perform(get("/backoffice/categories/{id}", phones.getId()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.slug").value("phones"))
            .andExpect(jsonPath("$.parentId").value(0));
        mockMvc.perform(get("/backoffice/categories/{id}", 999).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void updateCategory_admin_changesFields() throws Exception {
        Category phones = fixtures.category("Phones", "phones");
        Category electronics = fixtures.category("Electronics", "electronics");

        mockMvc.perform(json(put("/backoffice/categories/{id}", phones.getId()),
                body("Mobile phones", "mobile-phones", electronics.getId())).with(admin()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/backoffice/categories/{id}", phones.getId()).with(admin()))
            .andExpect(jsonPath("$.name").value("Mobile phones"))
            .andExpect(jsonPath("$.slug").value("mobile-phones"))
            .andExpect(jsonPath("$.parentId").value(electronics.getId()));
    }

    @Test
    void updateCategory_parentIsItself_returns400() throws Exception {
        Category phones = fixtures.category("Phones", "phones");

        mockMvc.perform(json(put("/backoffice/categories/{id}", phones.getId()),
                body("Phones", "phones", phones.getId())).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Parent category cannot be itself children"));
    }

    @Test
    void updateCategory_parentIsOwnDescendant_returns400() throws Exception {
        Category electronics = fixtures.category("Electronics", "electronics");
        Category phones = fixtures.category("Phones", "phones");
        phones.setParent(electronics);
        phones = fixtures.saveCategory(phones);

        mockMvc.perform(json(put("/backoffice/categories/{id}", electronics.getId()),
                body("Electronics", "electronics", phones.getId())).with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createCategory_duplicateName_orSlug_returns400() throws Exception {
        fixtures.category("Phones", "phones");

        mockMvc.perform(json(post("/backoffice/categories"), body("Phones", "phones-2", null)).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Request name Phones is already existed"));
        mockMvc.perform(json(post("/backoffice/categories"), body("Phones 2", "phones", null)).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Slug phones is already existed or is duplicated"));
    }

    @Test
    void updateCategory_duplicateSlugOfOther_returns400_ownSlugAllowed() throws Exception {
        fixtures.category("Phones", "phones");
        Category laptops = fixtures.category("Laptops", "laptops");

        mockMvc.perform(json(put("/backoffice/categories/{id}", laptops.getId()), body("Laptops", "phones", null))
                .with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(put("/backoffice/categories/{id}", laptops.getId()), body("Laptops", "laptops", null))
                .with(admin()))
            .andExpect(status().isNoContent());
    }

    @Test
    void deleteCategory_unused_returns204() throws Exception {
        Category phones = fixtures.category("Phones", "phones");

        mockMvc.perform(delete("/backoffice/categories/{id}", phones.getId()).with(admin()))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/categories/{id}", phones.getId()).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategory_withChildren_returns400() throws Exception {
        Category electronics = fixtures.category("Electronics", "electronics");
        Category phones = fixtures.category("Phones", "phones");
        phones.setParent(electronics);
        fixtures.saveCategory(phones);

        mockMvc.perform(delete("/backoffice/categories/{id}", electronics.getId()).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Please make sure this category contains no children"));
    }

    @Test
    void deleteCategory_withProducts_returns400() throws Exception {
        Category phones = fixtures.category("Phones", "phones");
        Product iphone = fixtures.product("iPhone", "iphone", 999);
        fixtures.inCategory(iphone, phones);

        mockMvc.perform(delete("/backoffice/categories/{id}", phones.getId()).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Please make sure this category contains no product"));
    }

    @Test
    void deleteCategory_unknown_returns404() throws Exception {
        mockMvc.perform(delete("/backoffice/categories/{id}", 999).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void customer_isForbidden() throws Exception {
        Category phones = fixtures.category("Phones", "phones");

        mockMvc.perform(get("/backoffice/categories").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/categories/{id}", phones.getId()).with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(json(post("/backoffice/categories"), body("A", "a", null)).with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/backoffice/categories/{id}", phones.getId()).with(customer()))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/backoffice/categories/1")).andExpect(status().isUnauthorized());
    }
}
