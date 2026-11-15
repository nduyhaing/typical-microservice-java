package com.shopapp.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * YAS GET /storefront/orders/completed?productId=: did the caller buy the product (or one of its variations)? Used by
 * the rating service before a customer may review a product (decisions.md 4-30).
 */
class OrderBoughtProductIT extends OrderItSupport {

    private void order(String customer, String items) throws Exception {
        String checkoutId = createCheckout(customer, items);
        mockMvc.perform(placeOrder(checkoutId, "").with(customer(customer))).andExpect(status().isOk());
    }

    private ResultActions bought(String customer, long productId) throws Exception {
        return mockMvc.perform(get("/storefront/orders/completed").param("productId", String.valueOf(productId))
            .with(customer(customer)));
    }

    @Test
    void orderedProduct_isPresentForTheBuyerOnly() throws Exception {
        order("u-1", "[{\"productId\":25,\"quantity\":1}]");

        bought("u-1", 25).andExpect(status().isOk()).andExpect(jsonPath("$.isPresent").value(true));
        // another customer's order does not count
        bought("u-2", 25).andExpect(status().isOk()).andExpect(jsonPath("$.isPresent").value(false));
        // a product the buyer did not order
        bought("u-1", 26).andExpect(status().isOk()).andExpect(jsonPath("$.isPresent").value(false));
    }

    @Test
    void orderedVariation_countsForTheParentProduct() throws Exception {
        order("u-1", "[{\"productId\":28,\"quantity\":1}]");

        bought("u-1", 40).andExpect(jsonPath("$.isPresent").value(true));
        bought("u-2", 40).andExpect(jsonPath("$.isPresent").value(false));
        // the variations are looked up with the caller's token
        assertThat(VARIATION_CALLS).extracting(Call::authorization)
            .containsExactly("Bearer token-u-1", "Bearer token-u-2");
    }

    @Test
    void cancelledOrRejectedOrder_doesNotCount() throws Exception {
        order("u-1", "[{\"productId\":25,\"quantity\":1}]");

        jdbc.update("update \"order\" set status = 'CANCELLED'");
        bought("u-1", 25).andExpect(jsonPath("$.isPresent").value(false));
        jdbc.update("update \"order\" set status = 'REJECT'");
        bought("u-1", 25).andExpect(jsonPath("$.isPresent").value(false));
        // shop_app orders stay PENDING_PAYMENT until payment (slice 5): placed counts as bought for now
        jdbc.update("update \"order\" set status = 'PENDING_PAYMENT'");
        bought("u-1", 25).andExpect(jsonPath("$.isPresent").value(true));
        jdbc.update("update \"order\" set status = 'COMPLETED'");
        bought("u-1", 25).andExpect(jsonPath("$.isPresent").value(true));
    }

    @Test
    void productUnknownToTheProductService_checksTheIdItself() throws Exception {
        bought("u-1", 404).andExpect(status().isOk()).andExpect(jsonPath("$.isPresent").value(false));
    }

    @Test
    void productServiceDown_is500() throws Exception {
        order("u-1", "[{\"productId\":25,\"quantity\":1}]");
        PRODUCT_DOWN.set(true);

        bought("u-1", 25).andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Product service is unavailable, please try again later"));
    }

    @Test
    void anonymous_isRefused_andProductIdIsRequired() throws Exception {
        mockMvc.perform(get("/storefront/orders/completed").param("productId", "25"))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/storefront/orders/completed").with(customer("u-1")))
            .andExpect(status().isBadRequest());
    }
}
