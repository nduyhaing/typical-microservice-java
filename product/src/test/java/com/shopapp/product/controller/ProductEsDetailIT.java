package com.shopapp.product.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.support.AbstractStorefrontIT;
import com.shopapp.product.support.DetailFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** {@code GET /storefront/products-es/{id}}: what the search service indexes (decisions.md 4-10). */
@Import(DetailFixtures.class)
class ProductEsDetailIT extends AbstractStorefrontIT {

    @Autowired
    private DetailFixtures detail;

    @Test
    void publishedVisibleProduct_returnsSearchFields() throws Exception {
        Brand samsung = fixtures.brand("Samsung", "samsung");
        Category phones = fixtures.category("Phones", "phones");
        Category android = fixtures.category("Android", "android");
        Product galaxy = fixtures.ofBrand(fixtures.product("Galaxy S24", "galaxy-s24", 799), samsung);
        galaxy.setFeatured(true);
        galaxy.setThumbnailMediaId(42L);
        galaxy = fixtures.save(galaxy);
        fixtures.inCategory(galaxy, phones);
        fixtures.inCategory(galaxy, android);
        detail.attribute(galaxy, "Display", "Size", "6.2 inch");
        detail.attribute(galaxy, "Battery", "Capacity", "4000 mAh");

        mockMvc.perform(get("/storefront/products-es/{id}", galaxy.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(galaxy.getId()))
            .andExpect(jsonPath("$.name").value("Galaxy S24"))
            .andExpect(jsonPath("$.slug").value("galaxy-s24"))
            .andExpect(jsonPath("$.price").value(799.0))
            .andExpect(jsonPath("$.isPublished").value(true))
            .andExpect(jsonPath("$.isVisibleIndividually").value(true))
            .andExpect(jsonPath("$.isAllowedToOrder").value(true))
            .andExpect(jsonPath("$.isFeatured").value(true))
            .andExpect(jsonPath("$.thumbnailMediaId").value(42))
            .andExpect(jsonPath("$.brand").value("Samsung"))
            .andExpect(jsonPath("$.categories").value(containsInAnyOrder("Phones", "Android")))
            .andExpect(jsonPath("$.attributes").value(containsInAnyOrder("Size", "Capacity")))
            .andExpect(jsonPath("$.createdOn").exists());
    }

    @Test
    void bareProduct_returnsNullBrandAndEmptyLists() throws Exception {
        Product cable = fixtures.product("USB-C Cable", "usb-c-cable", 9.9);

        mockMvc.perform(get("/storefront/products-es/{id}", cable.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brand").doesNotExist())
            .andExpect(jsonPath("$.categories").isEmpty())
            .andExpect(jsonPath("$.attributes").isEmpty())
            .andExpect(jsonPath("$.thumbnailMediaId").doesNotExist());
    }

    @Test
    void unpublishedProduct_returns404() throws Exception {
        Product draft = fixtures.product("Prototype", "prototype-phone", 1);
        draft.setPublished(false);
        fixtures.save(draft);

        mockMvc.perform(get("/storefront/products-es/{id}", draft.getId()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.name").doesNotExist());
    }

    @Test
    void variation_notVisibleIndividually_returns404() throws Exception {
        Product parent = fixtures.product("Galaxy S24", "galaxy-s24", 799);
        Product black = fixtures.variation(parent, "Galaxy S24 Black", "galaxy-s24-black", 799);

        mockMvc.perform(get("/storefront/products-es/{id}", black.getId())).andExpect(status().isNotFound());
    }

    @Test
    void unknownProduct_returns404() throws Exception {
        mockMvc.perform(get("/storefront/products-es/{id}", 999_999)).andExpect(status().isNotFound());
    }

    @Test
    void anonymousCaller_isAllowed() throws Exception {
        Product cable = fixtures.product("USB-C Cable", "usb-c-cable", 9.9);

        mockMvc.perform(get("/storefront/products-es/{id}", cable.getId())).andExpect(status().isOk());
    }
}
