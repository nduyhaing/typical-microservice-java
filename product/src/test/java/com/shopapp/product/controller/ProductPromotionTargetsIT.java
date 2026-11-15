package com.shopapp.product.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Brand;
import com.shopapp.product.model.Category;
import com.shopapp.product.model.Product;
import com.shopapp.product.support.AbstractStorefrontIT;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * GET /storefront/products/promotion-targets — what promotion needs to decide a coupon's products in one call
 * (decisions.md 4-74): orderable products only, a variation's brand and categories include its parent's.
 */
class ProductPromotionTargetsIT extends AbstractStorefrontIT {

    private static String ids(Product... products) {
        return Arrays.stream(products).map(p -> String.valueOf(p.getId())).collect(Collectors.joining(","));
    }

    @Test
    void targets_carryParentBrandAndCategories_variationInheritsFromItsParent() throws Exception {
        Brand samsung = fixtures.brand("Samsung", "samsung");
        Category phones = fixtures.category("Phones", "phones");
        Category accessories = fixtures.category("Accessories", "accessories");
        Product parent = fixtures.product("Galaxy S24", "galaxy-s24", 799);
        parent.setHasOptions(true);
        parent.setBrand(samsung);
        fixtures.save(parent);
        fixtures.inCategory(parent, phones);
        Product violet = fixtures.variation(parent, "Galaxy S24 Violet", "galaxy-s24-violet", 819);
        Product cable = fixtures.product("USB-C Cable", "usb-c-cable", 19);
        fixtures.inCategory(cable, accessories);

        mockMvc.perform(get("/storefront/products/promotion-targets").param("ids", ids(violet, cable)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[?(@.id == " + violet.getId() + ")].parentId").value(parent.getId().intValue()))
            .andExpect(jsonPath("$[?(@.id == " + violet.getId() + ")].brandId").value(samsung.getId().intValue()))
            .andExpect(jsonPath("$[?(@.id == " + violet.getId() + ")].categoryIds[*]")
                .value(containsInAnyOrder(phones.getId().intValue())))
            .andExpect(jsonPath("$[?(@.id == " + violet.getId() + ")].price").value(819.0))
            .andExpect(jsonPath("$[?(@.id == " + cable.getId() + ")].categoryIds[*]")
                .value(containsInAnyOrder(accessories.getId().intValue())));
    }

    @Test
    void targets_leaveOutWhatCannotBeOrdered() throws Exception {
        Product parentWithOptions = fixtures.product("Galaxy S24", "galaxy-s24", 799);
        parentWithOptions.setHasOptions(true);
        fixtures.save(parentWithOptions);
        Product unpublished = fixtures.product("Prototype", "prototype", 1);
        unpublished.setPublished(false);
        fixtures.save(unpublished);
        Product ok = fixtures.product("USB-C Cable", "usb-c-cable", 19);

        mockMvc.perform(get("/storefront/products/promotion-targets")
                .param("ids", ids(parentWithOptions, unpublished, ok) + ",999999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(ok.getId().intValue())));
    }

    @Test
    void targets_tooManyOrNoIds_return400() throws Exception {
        String tooMany = LongStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));

        mockMvc.perform(get("/storefront/products/promotion-targets").param("ids", tooMany))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/products/promotion-targets")).andExpect(status().isBadRequest());
    }
}
