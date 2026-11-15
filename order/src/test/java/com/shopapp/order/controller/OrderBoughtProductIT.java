package com.shopapp.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * YAS GET /storefront/orders/completed?productId=: did the caller buy the product (or one of its variations)? Used by
 * the rating service before a customer may review a product (decisions.md 4-30): only a PAID, SHIPPING or COMPLETED
 * order counts (5-3).
 */
class OrderBoughtProductIT extends OrderItSupport {

    /** A paid order (placed orders are PENDING_PAYMENT until payment). */
    private void order(String customer, String items) throws Exception {
        long id = placedOrder(customer, items, "");
        jdbc.update("update \"order\" set status = 'PAID' where id = ?", id);
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
    void onlyPaidShippingOrCompletedOrders_count() throws Exception {
        order("u-1", "[{\"productId\":25,\"quantity\":1}]");

        for (String status : List.of("PAID", "SHIPPING", "COMPLETED")) {
            jdbc.update("update \"order\" set status = ?", status);
            bought("u-1", 25).andExpect(jsonPath("$.isPresent").value(true));
        }
        // placed but not paid (4-30's interim rule counted these), accepted cash on delivery, or ended
        for (String status : List.of("PENDING", "PENDING_PAYMENT", "ACCEPTED", "CANCELLED", "REJECT", "REFUND")) {
            jdbc.update("update \"order\" set status = ?", status);
            bought("u-1", 25).andExpect(jsonPath("$.isPresent").value(false));
        }
    }

    @Test
    void justPlacedOrder_doesNotCountUntilPaid() throws Exception {
        placedOrder("u-1", "[{\"productId\":25,\"quantity\":1}]", "");

        bought("u-1", 25).andExpect(jsonPath("$.isPresent").value(false));
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
