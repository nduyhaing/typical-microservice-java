package com.shopapp.product.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Product;
import com.shopapp.product.support.AbstractStorefrontIT;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

/**
 * GET /products — the order service's lookup of names and prices at checkout (YAS), orderable products only.
 */
class ProductCheckoutListIT extends AbstractStorefrontIT {

    private static String ids(Product... products) {
        return java.util.Arrays.stream(products).map(p -> String.valueOf(p.getId())).collect(Collectors.joining(","));
    }

    @Test
    void checkoutList_returnsOrderableProductsWithPrice() throws Exception {
        Product parent = fixtures.product("Galaxy S24", "galaxy-s24", 799);
        parent.setHasOptions(true);
        fixtures.save(parent);
        Product violet = fixtures.variation(parent, "Galaxy S24 Violet", "galaxy-s24-violet", 819);
        Product cable = fixtures.product("USB-C Cable", "usb-c-cable", 19);

        mockMvc.perform(get("/products").param("ids", ids(violet, cable)).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productCheckoutListVms[*].name")
                .value(containsInAnyOrder("Galaxy S24 Violet", "USB-C Cable")))
            .andExpect(jsonPath("$.productCheckoutListVms[?(@.name == 'Galaxy S24 Violet')].price").value(819.0));
    }

    @Test
    void checkoutList_excludesNotOrderable() throws Exception {
        Product parentWithOptions = fixtures.product("Galaxy S24", "galaxy-s24", 799);
        parentWithOptions.setHasOptions(true);
        fixtures.save(parentWithOptions);
        Product unpublished = fixtures.product("Prototype", "prototype", 1);
        unpublished.setPublished(false);
        fixtures.save(unpublished);
        Product notAllowed = fixtures.product("Display model", "display-model", 5);
        notAllowed.setAllowedToOrder(false);
        fixtures.save(notAllowed);
        Product ok = fixtures.product("USB-C Cable", "usb-c-cable", 19);

        mockMvc.perform(get("/products").param("ids", ids(parentWithOptions, unpublished, notAllowed, ok)).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productCheckoutListVms[*].slug").doesNotExist())
            .andExpect(jsonPath("$.productCheckoutListVms[*].name").value(containsInAnyOrder("USB-C Cable")));
    }

    @Test
    void checkoutList_productWithoutBrand_ok() throws Exception {
        Product cable = fixtures.product("USB-C Cable", "usb-c-cable", 19);

        mockMvc.perform(get("/products").param("ids", ids(cable)).with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.productCheckoutListVms[0].brandId").doesNotExist());
    }

    @Test
    void checkoutList_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/products").param("ids", "1")).andExpect(status().isUnauthorized());
    }

    @Test
    void checkoutList_tooManyIds_returns400() throws Exception {
        String tooMany = LongStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));

        mockMvc.perform(get("/products").param("ids", tooMany).with(jwt())).andExpect(status().isBadRequest());
    }
}
