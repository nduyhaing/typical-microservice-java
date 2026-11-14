package com.shopapp.product.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.model.ProductOption;
import com.shopapp.product.support.AbstractStorefrontIT;
import com.shopapp.product.support.DetailFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(DetailFixtures.class)
class ProductDetailControllerIT extends AbstractStorefrontIT {

    @Autowired
    private DetailFixtures detail;

    /** Galaxy S24 with options Color {Black 799, Violet 819}. Returns the parent. */
    private Product galaxyWithVariations() {
        Product parent = fixtures.product("Galaxy S24", "galaxy-s24", 799);
        parent.setHasOptions(true);
        parent = fixtures.save(parent);
        ProductOption color = detail.option("Color");
        Product black = fixtures.variation(parent, "Galaxy S24 Black", "galaxy-s24-black", 799);
        Product violet = fixtures.variation(parent, "Galaxy S24 Violet", "galaxy-s24-violet", 819);
        detail.combination(black, color, "Black");
        detail.combination(violet, color, "Violet");
        return parent;
    }

    @Test
    void getProductDetail_returnsGroupedAttributes() throws Exception {
        Brand samsung = fixtures.brand("Samsung", "samsung");
        Category phones = fixtures.category("Phones", "phones");
        Product galaxy = fixtures.ofBrand(fixtures.product("Galaxy S24", "galaxy-s24", 799), samsung);
        fixtures.inCategory(galaxy, phones);
        detail.attribute(galaxy, "Display", "Size", "6.2 inch");
        detail.attribute(galaxy, "Battery", "Capacity", "4000 mAh");

        mockMvc.perform(get("/storefront/product/galaxy-s24"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Galaxy S24"))
            .andExpect(jsonPath("$.brandName").value("Samsung"))
            .andExpect(jsonPath("$.price").value(799.0))
            .andExpect(jsonPath("$.productCategories").value(containsInAnyOrder("Phones")))
            .andExpect(jsonPath("$.productAttributeGroups[*].name").value(containsInAnyOrder("Display", "Battery")))
            .andExpect(jsonPath("$.productAttributeGroups[?(@.name == 'Display')].productAttributeValues[0].value")
                .value("6.2 inch"));
    }

    @Test
    void getProductDetail_bareProduct_returnsEmptyCollections() throws Exception {
        fixtures.product("USB-C Cable", "usb-c-cable", 9.9);

        mockMvc.perform(get("/storefront/product/usb-c-cable"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.brandName").doesNotExist())
            .andExpect(jsonPath("$.productCategories").isEmpty())
            .andExpect(jsonPath("$.productAttributeGroups").isEmpty())
            .andExpect(jsonPath("$.productImageMediaUrls").isEmpty())
            .andExpect(jsonPath("$.thumbnailMediaUrl").value(""));
    }

    @Test
    void getProductDetail_unknownSlug_returns404() throws Exception {
        mockMvc.perform(get("/storefront/product/does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.statusCode").value("404 NOT_FOUND"));
    }

    @Test
    void getProductDetail_unpublished_returns404() throws Exception {
        Product draft = fixtures.product("Prototype", "prototype-phone", 1);
        draft.setPublished(false);
        fixtures.save(draft);

        mockMvc.perform(get("/storefront/product/prototype-phone")).andExpect(status().isNotFound());
    }

    @Test
    void getVariations_returnsChildrenWithOptions() throws Exception {
        Product parent = galaxyWithVariations();

        mockMvc.perform(get("/storefront/product-variations/{id}", parent.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].price").value(containsInAnyOrder(799.0, 819.0)))
            .andExpect(jsonPath("$[?(@.slug == 'galaxy-s24-violet')].price").value(819.0))
            .andExpect(jsonPath("$[*].options.*").value(containsInAnyOrder("Black", "Violet")));
    }

    @Test
    void getVariations_productWithoutOptions_returnsEmpty() throws Exception {
        Product cable = fixtures.product("USB-C Cable", "usb-c-cable", 9.9);

        mockMvc.perform(get("/storefront/product-variations/{id}", cable.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getOptionValues_returnsDistinctOptionValuesOfVariations() throws Exception {
        Product parent = galaxyWithVariations();

        mockMvc.perform(get("/storefront/product-option-combinations/{id}/values", parent.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].productOptionName").value(containsInAnyOrder("Color", "Color")))
            .andExpect(jsonPath("$[*].productOptionValue").value(containsInAnyOrder("Black", "Violet")));
    }

    @Test
    void getOptionValues_unknownProduct_returns404() throws Exception {
        mockMvc.perform(get("/storefront/product-option-combinations/{id}/values", 999_999))
            .andExpect(status().isNotFound());
    }
}
