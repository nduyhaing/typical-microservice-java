package com.shopapp.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Slice 4: a coupon on preview and place order (decisions.md 4-50..4-59). Discount on the eligible lines only, tax
 * on the discounted line amounts, one use taken from promotion before the commit and given back when the order is
 * not saved.
 */
class OrderCouponIT extends OrderItSupport {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    /** 25 x 2 = 1638.00 (10 % tax at address 100), 26 = 19.00 (no rate), 27 = 10.00 (no class): 1667.00. */
    private static final String MIXED_ITEMS = "[{\"productId\":25,\"quantity\":2},{\"productId\":26,\"quantity\":1},"
        + "{\"productId\":27,\"quantity\":1}]";

    private BigDecimal decimal(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    private String checkoutState(String checkoutId) {
        return jdbc.queryForObject("select status from checkout where id = ?", String.class, checkoutId);
    }

    private MockHttpServletRequestBuilder placeWithCoupon(String checkoutId, long addressId, String couponCode) {
        return json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressId\":"
            + addressId + ",\"paymentMethod\":\"COD\",\"couponCode\":\"" + couponCode + "\"}");
    }

    private MockHttpServletRequestBuilder previewWithCoupon(String checkoutId, long addressId, String couponCode) {
        return json(post("/storefront/orders/preview"), "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressId\":"
            + addressId + (couponCode == null ? "" : ",\"couponCode\":\"" + couponCode + "\"") + "}");
    }

    @Test
    void placeOrder_percentageCoupon_discountsEligibleLinesOnly_andTaxesTheDiscountedAmount() throws Exception {
        VERIFY_ANSWER.set(validCoupon("SAVE10", "PERCENTAGE", "10", "25"));
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        // line 25: 1638.00 - 163.80 = 1474.20, tax 147.42; total = 1667.00 - 163.80 + 147.42
        mockMvc.perform(placeWithCoupon(checkoutId, 100, " save10 ").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotal").value(1667.0))
            .andExpect(jsonPath("$.discount").value(163.8))
            .andExpect(jsonPath("$.tax").value(147.42))
            .andExpect(jsonPath("$.totalPrice").value(1650.62))
            .andExpect(jsonPath("$.couponCode").value("SAVE10"))
            .andExpect(jsonPath("$.orderItemVms[?(@.productId == 25)].discountAmount").value(163.8))
            .andExpect(jsonPath("$.orderItemVms[?(@.productId == 26)].discountAmount").value(0.0));

        assertThat(jdbc.queryForObject("select promotion_code from \"order\"", String.class)).isEqualTo("SAVE10");
        assertThat(decimal("select total_discount_amount from \"order\"")).isEqualByComparingTo("163.80");
        assertThat(decimal("select total_tax from \"order\"")).isEqualByComparingTo("147.42");
        assertThat(decimal("select total_amount from \"order\"")).isEqualByComparingTo("1650.62");
        assertThat(decimal("select discount_amount from order_item where product_id = 25")).isEqualByComparingTo("163.80");
        assertThat(decimal("select tax_amount from order_item where product_id = 25")).isEqualByComparingTo("147.42");
        assertThat(decimal("select discount_amount from order_item where product_id = 26")).isEqualByComparingTo("0");
        assertThat(decimal("select discount_amount from order_item where product_id = 27")).isEqualByComparingTo("0");

        // verify: the trimmed code, the pre-discount subtotal and every product of the checkout, customer's token
        assertThat(VERIFY_CALLS).hasSize(1);
        Call verify = VERIFY_CALLS.getFirst();
        assertThat(verify.authorization()).isEqualTo("Bearer token-u-1");
        assertThat(JsonPath.<String>read(verify.body(), "$.couponCode")).isEqualTo("save10");
        assertThat(JsonPath.<Double>read(verify.body(), "$.orderPrice")).isEqualTo(1667.0);
        assertThat(JsonPath.<List<Integer>>read(verify.body(), "$.productIds")).containsExactly(25, 26, 27);

        // one use taken under its own server-side reference, with the eligible ids, stored on the order
        assertThat(APPLY_CALLS).hasSize(1);
        Call apply = APPLY_CALLS.getFirst();
        assertThat(apply.authorization()).isEqualTo("Bearer token-u-1");
        assertThat(apply.param("reference")).matches(UUID_PATTERN)
            .isNotEqualTo(SUBTRACT_CALLS.getFirst().param("reference"));
        assertThat(JsonPath.<String>read(apply.body(), "$.couponCode")).isEqualTo("SAVE10");
        assertThat(JsonPath.<List<Integer>>read(apply.body(), "$.productIds")).containsExactly(25);
        assertThat(jdbc.queryForObject("select promotion_usage_reference::text from \"order\"", String.class))
            .isEqualTo(apply.param("reference"));
        assertThat(RELEASE_CALLS).isEmpty();
        assertThat(RESTORE_CALLS).isEmpty();
    }

    @Test
    void placeOrder_fixedCoupon_distributedProportionally_remainderOnTheLastEligibleLine() throws Exception {
        VERIFY_ANSWER.set(validCoupon("TEN", "FIXED", "10", "25,26,27"));
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        // 10 x 1638/1667 = 9.83, 10 x 19/1667 = 0.11, last = 10 - 9.94 = 0.06; tax (1638 - 9.83) x 10 % = 162.82
        mockMvc.perform(placeWithCoupon(checkoutId, 100, "TEN").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount").value(10.0))
            .andExpect(jsonPath("$.tax").value(162.82))
            .andExpect(jsonPath("$.totalPrice").value(1819.82));

        assertThat(decimal("select discount_amount from order_item where product_id = 25")).isEqualByComparingTo("9.83");
        assertThat(decimal("select discount_amount from order_item where product_id = 26")).isEqualByComparingTo("0.11");
        assertThat(decimal("select discount_amount from order_item where product_id = 27")).isEqualByComparingTo("0.06");
        assertThat(decimal("select total_discount_amount from \"order\"")).isEqualByComparingTo("10.00");
    }

    @Test
    void placeOrder_fixedCouponAboveTheEligibleSubtotal_isCapped_neverNegative() throws Exception {
        VERIFY_ANSWER.set(validCoupon("FIFTY", "FIXED", "50", "26,27"));
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        // eligible 26 + 27 = 29.00: the discount is 29.00, those lines cost 0, line 25 keeps its full price and tax
        mockMvc.perform(placeWithCoupon(checkoutId, 100, "FIFTY").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount").value(29.0))
            .andExpect(jsonPath("$.tax").value(163.8))
            .andExpect(jsonPath("$.totalPrice").value(1801.8));

        assertThat(decimal("select discount_amount from order_item where product_id = 26")).isEqualByComparingTo("19.00");
        assertThat(decimal("select discount_amount from order_item where product_id = 27")).isEqualByComparingTo("10.00");
        assertThat(decimal("select discount_amount from order_item where product_id = 25")).isEqualByComparingTo("0");
    }

    @Test
    void preview_withCoupon_matchesThePlacedOrder_andTakesNoUse() throws Exception {
        VERIFY_ANSWER.set(validCoupon("SAVE10", "PERCENTAGE", "10", "25"));
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        String preview = mockMvc.perform(previewWithCoupon(checkoutId, 100, "SAVE10").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotal").value(1667.0))
            .andExpect(jsonPath("$.discount").value(163.8))
            .andExpect(jsonPath("$.totalTax").value(147.42))
            .andExpect(jsonPath("$.total").value(1650.62))
            .andExpect(jsonPath("$.couponCode").value("SAVE10"))
            .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Double>>read(preview, "$.items[?(@.productId == 25)].discountAmount"))
            .containsExactly(163.8);
        assertThat(JsonPath.<List<Double>>read(preview, "$.items[?(@.productId == 25)].taxAmount"))
            .containsExactly(147.42);
        assertThat(APPLY_CALLS).isEmpty();
        assertThat(SUBTRACT_CALLS).isEmpty();

        String order = mockMvc.perform(placeWithCoupon(checkoutId, 100, "SAVE10").with(customer("u-1")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Double>read(order, "$.totalPrice")).isEqualTo(JsonPath.<Double>read(preview, "$.total"));
        assertThat(JsonPath.<Double>read(order, "$.tax")).isEqualTo(JsonPath.<Double>read(preview, "$.totalTax"));
        assertThat(JsonPath.<Double>read(order, "$.discount")).isEqualTo(JsonPath.<Double>read(preview, "$.discount"));
    }

    @Test
    void invalidCoupon_previewAndPlaceOrder_return400WithPromotionsCode_andTakeNothing() throws Exception {
        VERIFY_ANSWER.set(promotionError("PROMOTION_EXPIRED", "Promotion OLD has expired"));
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        mockMvc.perform(previewWithCoupon(checkoutId, 100, "OLD").with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("PROMOTION_EXPIRED"))
            .andExpect(jsonPath("$.detail").value("Promotion OLD has expired"));
        mockMvc.perform(placeWithCoupon(checkoutId, 100, "OLD").with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("PROMOTION_EXPIRED"));

        assertThat(SUBTRACT_CALLS).isEmpty();
        assertThat(APPLY_CALLS).isEmpty();
        assertThat(orderCount()).isZero();
        assertThat(checkoutState(checkoutId)).isEqualTo("PENDING");
    }

    @Test
    void placeOrder_couponUsedUpAtApplyTime_returns400_restoresStock_savesNothing() throws Exception {
        VERIFY_ANSWER.set(validCoupon("ONCE", "PERCENTAGE", "10", "25"));
        APPLY_ANSWER.set(promotionError("PROMOTION_USED_UP", "Promotion ONCE has been used up"));
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        mockMvc.perform(placeWithCoupon(checkoutId, 100, "ONCE").with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("PROMOTION_USED_UP"))
            .andExpect(jsonPath("$.detail").value("Promotion ONCE has been used up"));

        assertThat(orderCount()).isZero();
        assertThat(checkoutState(checkoutId)).isEqualTo("PENDING");
        assertThat(RESTORE_CALLS).hasSize(1);
        assertThat(RESTORE_CALLS.getFirst().param("reference")).isEqualTo(SUBTRACT_CALLS.getFirst().param("reference"));
        // a 4xx answer took no use: nothing to give back
        assertThat(RELEASE_CALLS).isEmpty();
    }

    @Test
    void placeOrder_databaseFailsAfterTheUseWasTaken_releasesTheUseAndRestoresTheStock() throws Exception {
        VERIFY_ANSWER.set(validCoupon("SAVE10", "PERCENTAGE", "10", "25"));
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        // saved address 500 has a line longer than any order column: the insert fails after apply-usage
        mockMvc.perform(placeWithCoupon(checkoutId, 500, "SAVE10").with(customer("u-1")))
            .andExpect(status().isInternalServerError());

        assertThat(orderCount()).isZero();
        assertThat(APPLY_CALLS).hasSize(1);
        assertThat(RELEASE_CALLS).hasSize(1);
        assertThat(RELEASE_CALLS.getFirst().param("reference")).isEqualTo(APPLY_CALLS.getFirst().param("reference"));
        assertThat(RELEASE_CALLS.getFirst().authorization()).isEqualTo("Bearer token-u-1");
        assertThat(RESTORE_CALLS).hasSize(1);
        assertThat(RESTORE_CALLS.getFirst().param("reference")).isEqualTo(SUBTRACT_CALLS.getFirst().param("reference"));
    }

    @Test
    void placeOrder_promotionDownAtApplyTime_returns5xx_releasesAndRestores() throws Exception {
        VERIFY_ANSWER.set(validCoupon("SAVE10", "PERCENTAGE", "10", "25"));
        APPLY_ANSWER.set(new Answer(503, ""));
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        mockMvc.perform(placeWithCoupon(checkoutId, 100, "SAVE10").with(customer("u-1")))
            .andExpect(status().is5xxServerError());

        // the outcome of a 5xx is unknown: release is safe (it gives back only this reference's own use)
        assertThat(orderCount()).isZero();
        assertThat(RELEASE_CALLS).hasSize(1);
        assertThat(RELEASE_CALLS.getFirst().param("reference")).isEqualTo(APPLY_CALLS.getFirst().param("reference"));
        assertThat(RESTORE_CALLS).hasSize(1);
    }

    @Test
    void promotionDown_withACoupon_previewAndPlaceOrderReturn5xx_andSaveNothing() throws Exception {
        PROMOTION_DOWN.set(true);
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        mockMvc.perform(previewWithCoupon(checkoutId, 100, "SAVE10").with(customer("u-1")))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Promotion service is unavailable, please try again later"));
        mockMvc.perform(placeWithCoupon(checkoutId, 100, "SAVE10").with(customer("u-1")))
            .andExpect(status().isInternalServerError());

        assertThat(orderCount()).isZero();
        assertThat(SUBTRACT_CALLS).isEmpty();
        assertThat(checkoutState(checkoutId)).isEqualTo("PENDING");
    }

    @Test
    void withoutACoupon_promotionIsNeverCalled() throws Exception {
        PROMOTION_DOWN.set(true);
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        mockMvc.perform(previewWithCoupon(checkoutId, 100, null).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount").value(0));
        mockMvc.perform(previewWithCoupon(checkoutId, 100, "  ").with(customer("u-1")))
            .andExpect(status().isOk());
        mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.discount").value(0))
            .andExpect(jsonPath("$.totalPrice").value(1830.8));

        assertThat(jdbc.queryForObject("select promotion_code from \"order\"", String.class)).isNull();
        assertThat(VERIFY_CALLS).isEmpty();
        assertThat(APPLY_CALLS).isEmpty();
        assertThat(RELEASE_CALLS).isEmpty();
    }

    @Test
    void couponCodeLongerThan100Characters_is400() throws Exception {
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        mockMvc.perform(previewWithCoupon(checkoutId, 100, "A".repeat(101)).with(customer("u-1")))
            .andExpect(status().isBadRequest());

        assertThat(VERIFY_CALLS).isEmpty();
    }
}
