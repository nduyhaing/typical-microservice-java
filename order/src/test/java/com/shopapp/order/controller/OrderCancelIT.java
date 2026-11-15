package com.shopapp.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.order.service.OrderGoodsBackService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The customer cancels their own unpaid order; its stock and coupon use are given back once through product's and
 * promotion's internal endpoints with the order service's own token (decisions.md 5-1, 5-2, 5-4, 5-5).
 */
class OrderCancelIT extends OrderItSupport {

    private static final String ONE_PHONE = "[{\"productId\":25,\"quantity\":2}]";
    private static final String WITH_COUPON = ",\"couponCode\":\"save10\"";

    @Autowired
    private OrderGoodsBackService goodsBackService;

    private Map<String, Object> orderRow(long id) {
        return jdbc.queryForMap("select status, payment_status, shipment_status, stock_reference,"
            + " promotion_usage_reference::text as coupon_reference, goods_back_requested_on, stock_restored_on,"
            + " promotion_usage_released_on from \"order\" where id = ?", id);
    }

    private long couponOrder(String customer) throws Exception {
        VERIFY_ANSWER.set(validCoupon("SAVE10", "PERCENTAGE", "10", "25"));
        return placedOrder(customer, ONE_PHONE, WITH_COUPON);
    }

    @Test
    void placeOrder_storesTheStockReferenceItSentToProduct() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        assertThat(SUBTRACT_CALLS).hasSize(1);
        assertThat(orderRow(id).get("stock_reference")).isEqualTo(SUBTRACT_CALLS.getFirst().param("reference"));
    }

    @Test
    void customerCancelsOwnUnpaidOrder_stockAndCouponComeBackOnce_withTheServiceToken() throws Exception {
        long id = couponOrder("u-1");
        Map<String, Object> placed = orderRow(id);
        assertThat(placed.get("coupon_reference")).isEqualTo(APPLY_CALLS.getFirst().param("reference"));

        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.orderStatus").value("CANCELLED"))
            .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"))
            .andExpect(jsonPath("$.deliveryStatus").value("CANCELLED"))
            .andExpect(jsonPath("$.cancellable").value(false));

        assertThat(INTERNAL_RESTORE_CALLS).singleElement().satisfies(call -> {
            assertThat(call.param("reference")).isEqualTo(placed.get("stock_reference"));
            assertThat(call.authorization()).isEqualTo("Bearer " + SERVICE_TOKEN);
        });
        assertThat(INTERNAL_RELEASE_CALLS).singleElement().satisfies(call -> {
            assertThat(call.param("reference")).isEqualTo(placed.get("coupon_reference"));
            assertThat(call.authorization()).isEqualTo("Bearer " + SERVICE_TOKEN);
        });
        // the customer-bound compensation endpoints of place order are not used for a cancellation
        assertThat(RESTORE_CALLS).isEmpty();
        assertThat(RELEASE_CALLS).isEmpty();
        assertThat(TOKEN_CALLS).singleElement().satisfies(call -> assertThat(call.body())
            .contains("grant_type=client_credentials", "client_id=order-service", "client_secret=order-secret"));
        Map<String, Object> cancelled = orderRow(id);
        assertThat(cancelled.get("status")).isEqualTo("CANCELLED");
        assertThat(cancelled.get("goods_back_requested_on")).isNotNull();
        assertThat(cancelled.get("stock_restored_on")).isNotNull();
        assertThat(cancelled.get("promotion_usage_released_on")).isNotNull();

        // a retry answers the cancelled order again and gives nothing back twice
        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
        goodsBackService.retryPending();
        assertThat(INTERNAL_RESTORE_CALLS).hasSize(1);
        assertThat(INTERNAL_RELEASE_CALLS).hasSize(1);
    }

    @Test
    void orderWithoutCoupon_onlyGivesTheStockBack() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1"))).andExpect(status().isOk());

        assertThat(INTERNAL_RESTORE_CALLS).hasSize(1);
        assertThat(INTERNAL_RELEASE_CALLS).isEmpty();
    }

    @Test
    void myOrders_sayWhichOrdersCanBeCancelled() throws Exception {
        long unpaid = placedOrder("u-1", ONE_PHONE, "");
        long paid = placedOrder("u-1", ONE_PHONE, "");
        jdbc.update("update \"order\" set status = 'PAID' where id = ?", paid);

        mockMvc.perform(get("/storefront/orders/my-orders").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + unpaid + ")].cancellable", contains(true)))
            .andExpect(jsonPath("$[?(@.id == " + paid + ")].cancellable", contains(false)));
    }

    @Test
    void someoneElsesOrUnknownOrder_is404_andNothingChanges() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-2")))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/storefront/orders/{id}/cancel", 999_999).with(customer("u-1")))
            .andExpect(status().isNotFound());

        assertThat(orderRow(id).get("status")).isEqualTo("PENDING_PAYMENT");
        assertThat(INTERNAL_RESTORE_CALLS).isEmpty();
    }

    @Test
    void anonymous_isRefused() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(csrf())).andExpect(status().isForbidden());
        assertThat(orderRow(id).get("status")).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void paidOrAcceptedOrder_cannotBeCancelledByTheCustomer() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");
        for (String status : List.of("PAID", "ACCEPTED", "SHIPPING", "COMPLETED", "REJECT")) {
            jdbc.update("update \"order\" set status = ? where id = ?", status, id);

            mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Order " + id + " can no longer be cancelled (status "
                    + status + ")"));
            assertThat(orderRow(id).get("status")).isEqualTo(status);
        }
        assertThat(INTERNAL_RESTORE_CALLS).isEmpty();
    }

    @Test
    void productOrPromotionDown_theOrderIsCancelled_andTheRestIsRetriedOnce() throws Exception {
        long id = couponOrder("u-1");
        INTERNAL_RESTORE_STATUS.set(503);

        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
        Map<String, Object> pending = orderRow(id);
        assertThat(pending.get("stock_restored_on")).isNull();
        assertThat(pending.get("promotion_usage_released_on")).isNotNull();

        // still down: the sweep tries again and records nothing
        goodsBackService.retryPending();
        assertThat(orderRow(id).get("stock_restored_on")).isNull();

        // back up: the sweep gives the stock back; the coupon (already released) is not released again
        INTERNAL_RESTORE_STATUS.set(204);
        goodsBackService.retryPending();
        assertThat(orderRow(id).get("stock_restored_on")).isNotNull();
        assertThat(INTERNAL_RESTORE_CALLS).hasSize(3)
            .allSatisfy(call -> assertThat(call.param("reference")).isEqualTo(pending.get("stock_reference")));
        assertThat(INTERNAL_RELEASE_CALLS).hasSize(1);

        // a later cancel retry calls nothing more
        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1"))).andExpect(status().isOk());
        assertThat(INTERNAL_RESTORE_CALLS).hasSize(3);
    }

    @Test
    void keycloakDown_theOrderIsCancelled_andTheGoodsAreGivenBackOnTheRetry() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");
        TOKEN_STATUS.set(503);

        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
        assertThat(INTERNAL_RESTORE_CALLS).isEmpty();
        assertThat(orderRow(id).get("stock_restored_on")).isNull();

        TOKEN_STATUS.set(200);
        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1"))).andExpect(status().isOk());
        assertThat(INTERNAL_RESTORE_CALLS).hasSize(1);
        assertThat(orderRow(id).get("stock_restored_on")).isNotNull();
    }

    @Test
    void productRejectsTheServiceToken_onceANewTokenIsFetched_thenGivesUp() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");
        INTERNAL_RESTORE_STATUS.set(401);

        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1"))).andExpect(status().isOk());

        // 401 -> cached token dropped, one more try with a new token, then left for the retry
        assertThat(INTERNAL_RESTORE_CALLS).hasSize(2);
        assertThat(TOKEN_CALLS).hasSizeGreaterThanOrEqualTo(1);
        assertThat(orderRow(id).get("stock_restored_on")).isNull();
    }

    @Test
    void orderPlacedBeforeSlice5_withoutStockReference_isCancelledWithoutAStockCall() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");
        jdbc.update("update \"order\" set stock_reference = null where id = ?", id);

        mockMvc.perform(post("/storefront/orders/{id}/cancel", id).with(customer("u-1"))).andExpect(status().isOk());

        assertThat(INTERNAL_RESTORE_CALLS).isEmpty();
        assertThat(orderRow(id).get("status")).isEqualTo("CANCELLED");
    }
}
