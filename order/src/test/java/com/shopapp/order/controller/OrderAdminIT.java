package com.shopapp.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Back-office orders (decisions.md 5-1): list with filters and bounded paging, detail, status changes through the
 * status machine, admin cancel / reject / refund with the stock and coupon given back for unshipped orders (5-4).
 */
class OrderAdminIT extends OrderItSupport {

    private static final String ONE_PHONE = "[{\"productId\":25,\"quantity\":1}]";

    private ResultActions setStatus(long id, String body) throws Exception {
        return mockMvc.perform(json(put("/backoffice/orders/{id}/status", id), body).with(admin()));
    }

    private ResultActions setStatusTo(long id, String status) throws Exception {
        return setStatus(id, "{\"orderStatus\":\"" + status + "\"}");
    }

    private String statusOf(long id) {
        return jdbc.queryForObject("select status from \"order\" where id = ?", String.class, id);
    }

    // ---------- status machine ----------

    @Test
    void adminMovesAnOrderThroughPaidShippingCompleted() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        setStatusTo(id, "PAID").andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("PAID"))
            .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.allowedStatuses", containsInAnyOrder("SHIPPING", "REFUND")));
        setStatus(id, "{\"orderStatus\":\"SHIPPING\",\"deliveryMethod\":\"GRAB_EXPRESS\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("SHIPPING"))
            .andExpect(jsonPath("$.deliveryStatus").value("DELIVERING"))
            .andExpect(jsonPath("$.deliveryMethod").value("GRAB_EXPRESS"));
        setStatusTo(id, "COMPLETED").andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.deliveryStatus").value("DELIVERED"))
            .andExpect(jsonPath("$.allowedStatuses", containsInAnyOrder("REFUND")))
            .andExpect(jsonPath("$.lastModifiedOn").isNotEmpty());

        // nothing shipped was given back
        assertThat(INTERNAL_RESTORE_CALLS).isEmpty();
    }

    @Test
    void illegalTransitions_are400_andChangeNothing() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        setStatusTo(id, "COMPLETED").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Order " + id + " cannot go from PENDING_PAYMENT to COMPLETED"));
        setStatusTo(id, "SHIPPING").andExpect(status().isBadRequest());
        setStatusTo(id, "REFUND").andExpect(status().isBadRequest());
        assertThat(statusOf(id)).isEqualTo("PENDING_PAYMENT");

        setStatusTo(id, "PAID").andExpect(status().isOk());
        // a paid order is refunded, not cancelled
        setStatusTo(id, "CANCELLED").andExpect(status().isBadRequest());
        setStatusTo(id, "PENDING_PAYMENT").andExpect(status().isBadRequest());
        assertThat(statusOf(id)).isEqualTo("PAID");

        jdbc.update("update \"order\" set status = 'CANCELLED' where id = ?", id);
        setStatusTo(id, "PAID").andExpect(status().isBadRequest());
        assertThat(INTERNAL_RESTORE_CALLS).isEmpty();
    }

    @Test
    void sameStatus_changesOnlyTheDeliveryFields() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        setStatus(id, "{\"orderStatus\":\"PENDING_PAYMENT\",\"deliveryMethod\":\"VIETTEL_POST\","
            + "\"deliveryStatus\":\"PREPARING\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.deliveryMethod").value("VIETTEL_POST"));
    }

    @Test
    void badBody_is400() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        setStatus(id, "{}").andExpect(status().isBadRequest());
        setStatus(id, "{\"orderStatus\":\"SHIPPED\"}").andExpect(status().isBadRequest());
        setStatus(id, "{\"orderStatus\":\"REJECT\",\"rejectReason\":\"" + "x".repeat(256) + "\"}")
            .andExpect(status().isBadRequest());
        assertThat(statusOf(id)).isEqualTo("PENDING_PAYMENT");
        setStatusTo(999_999, "PAID").andExpect(status().isNotFound());
    }

    @Test
    void adminCancel_givesStockAndCouponBack_withTheServiceToken_notTheAdminToken() throws Exception {
        VERIFY_ANSWER.set(validCoupon("SAVE10", "PERCENTAGE", "10", "25"));
        long id = placedOrder("u-1", ONE_PHONE, ",\"couponCode\":\"SAVE10\"");
        String stockReference = SUBTRACT_CALLS.getFirst().param("reference");
        String couponReference = APPLY_CALLS.getFirst().param("reference");

        setStatusTo(id, "CANCELLED").andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("CANCELLED"))
            .andExpect(jsonPath("$.goodsBack.stockRestoredOn").isNotEmpty())
            .andExpect(jsonPath("$.goodsBack.couponReleasedOn").isNotEmpty())
            .andExpect(jsonPath("$.allowedStatuses").isEmpty());

        assertThat(INTERNAL_RESTORE_CALLS).singleElement().satisfies(call -> {
            assertThat(call.param("reference")).isEqualTo(stockReference);
            assertThat(call.authorization()).isEqualTo("Bearer " + SERVICE_TOKEN);
        });
        assertThat(INTERNAL_RELEASE_CALLS).singleElement().satisfies(call -> {
            assertThat(call.param("reference")).isEqualTo(couponReference);
            assertThat(call.authorization()).isEqualTo("Bearer " + SERVICE_TOKEN);
        });

        // the same status again: nothing given back twice
        setStatusTo(id, "CANCELLED").andExpect(status().isOk());
        assertThat(INTERNAL_RESTORE_CALLS).hasSize(1);
        assertThat(INTERNAL_RELEASE_CALLS).hasSize(1);
    }

    @Test
    void reject_keepsTheReason_andGivesTheGoodsBack() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        setStatus(id, "{\"orderStatus\":\"REJECT\",\"rejectReason\":\" Address does not exist \"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("REJECT"))
            .andExpect(jsonPath("$.rejectReason").value("Address does not exist"));
        assertThat(INTERNAL_RESTORE_CALLS).hasSize(1);
    }

    @Test
    void refundBeforeShipping_givesTheGoodsBack_refundAfterShipping_doesNot() throws Exception {
        long paid = placedOrder("u-1", ONE_PHONE, "");
        setStatusTo(paid, "PAID").andExpect(status().isOk());
        setStatusTo(paid, "REFUND").andExpect(status().isOk()).andExpect(jsonPath("$.orderStatus").value("REFUND"));
        assertThat(INTERNAL_RESTORE_CALLS).hasSize(1);

        long shipped = placedOrder("u-1", ONE_PHONE, "");
        setStatusTo(shipped, "PAID").andExpect(status().isOk());
        setStatusTo(shipped, "SHIPPING").andExpect(status().isOk());
        setStatusTo(shipped, "REFUND").andExpect(status().isOk())
            .andExpect(jsonPath("$.goodsBack").doesNotExist());
        assertThat(INTERNAL_RESTORE_CALLS).hasSize(1);
    }

    @Test
    void cashOnDelivery_acceptedShippedCompleted() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        setStatusTo(id, "ACCEPTED").andExpect(status().isOk())
            .andExpect(jsonPath("$.allowedStatuses", containsInAnyOrder("PAID", "SHIPPING", "CANCELLED", "REJECT")));
        setStatusTo(id, "SHIPPING").andExpect(status().isOk());
        setStatusTo(id, "COMPLETED").andExpect(status().isOk());
    }

    // ---------- detail ----------

    @Test
    void detail_showsItemsTotalsAddresses_butNeverTheReferences() throws Exception {
        long id = placedOrder("u-1", "[{\"productId\":25,\"quantity\":2},{\"productId\":27,\"quantity\":1}]", "");

        mockMvc.perform(get("/backoffice/orders/{id}", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.email").value("u-1@shopapp.local"))
            .andExpect(jsonPath("$.customerId").value("u-1"))
            .andExpect(jsonPath("$.subtotal").value(1648.0))
            .andExpect(jsonPath("$.totalPrice").value(1648.0))
            .andExpect(jsonPath("$.orderItemVms.length()").value(2))
            .andExpect(jsonPath("$.shippingAddressVm.city").value("Springfield"))
            .andExpect(jsonPath("$.billingAddressVm.city").value("Springfield"))
            .andExpect(jsonPath("$.allowedStatuses",
                containsInAnyOrder("ACCEPTED", "PAID", "CANCELLED", "REJECT")))
            .andExpect(jsonPath("$", not(hasKey("stockReference"))))
            .andExpect(jsonPath("$", not(hasKey("promotionUsageReference"))));
        mockMvc.perform(get("/backoffice/orders/{id}", 999_999).with(admin())).andExpect(status().isNotFound());
    }

    // ---------- list ----------

    @Test
    void list_filtersByStatusEmailProductAndDate_newestFirst() throws Exception {
        long phone = placedOrder("u-1", ONE_PHONE, "");
        long cable = placedOrder("u-2", "[{\"productId\":26,\"quantity\":1}]", "");
        long both = placedOrder("u-1", "[{\"productId\":26,\"quantity\":1},{\"productId\":27,\"quantity\":1}]", "");
        jdbc.update("update \"order\" set status = 'PAID' where id = ?", cable);
        jdbc.update("update \"order\" set created_on = '2026-01-10T10:00:00Z' where id = ?", phone);
        jdbc.update("update \"order\" set created_on = '2026-02-10T10:00:00Z' where id = ?", cable);
        jdbc.update("update \"order\" set created_on = '2026-03-10T10:00:00Z' where id = ?", both);

        list("").andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.orderList[*].id", contains((int) both, (int) cable, (int) phone)))
            .andExpect(jsonPath("$.orderList[0].billingAddressVm.countryName").value("United States"))
            .andExpect(jsonPath("$.orderList[0].orderStatus").value("PENDING_PAYMENT"));
        list("orderStatus=PAID").andExpect(jsonPath("$.orderList[*].id", contains((int) cable)));
        list("orderStatus=PAID,PENDING_PAYMENT").andExpect(jsonPath("$.totalElements").value(3));
        list("email=U-2@SHOP").andExpect(jsonPath("$.orderList[*].id", contains((int) cable)));
        list("productName=usb-c").andExpect(jsonPath("$.orderList[*].id", contains((int) both, (int) cable)));
        // LIKE wildcards are matched literally
        list("productName=%25").andExpect(jsonPath("$.totalElements").value(0));
        list("createdFrom=2026-02-01T00:00:00Z&createdTo=2026-02-28T23:59:59Z")
            .andExpect(jsonPath("$.orderList[*].id", contains((int) cable)));
        list("createdFrom=2026-02-01T00:00:00Z&productName=case")
            .andExpect(jsonPath("$.orderList[*].id", contains((int) both)));
    }

    @Test
    void list_pagesAreBounded() throws Exception {
        for (int i = 0; i < 3; i++) {
            placedOrder("u-1", ONE_PHONE, "");
        }

        list("pageNo=1&pageSize=2").andExpect(jsonPath("$.orderList.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2));
        list("pageSize=101").andExpect(status().isBadRequest());
        list("pageSize=0").andExpect(status().isBadRequest());
        list("pageNo=-1").andExpect(status().isBadRequest());
        list("orderStatus=NOPE").andExpect(status().isBadRequest());
        list("email=" + "x".repeat(256)).andExpect(status().isBadRequest());
    }

    private ResultActions list(String query) throws Exception {
        return mockMvc.perform(get("/backoffice/orders?" + query).with(admin()));
    }

    // ---------- security ----------

    @Test
    void customersAndAnonymous_areRefused() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        mockMvc.perform(get("/backoffice/orders").with(customer("u-1"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/orders/{id}", id).with(customer("u-1"))).andExpect(status().isForbidden());
        mockMvc.perform(json(put("/backoffice/orders/{id}/status", id), "{\"orderStatus\":\"PAID\"}")
            .with(customer("u-1"))).andExpect(status().isForbidden());
        mockMvc.perform(json(put("/backoffice/orders/{id}/status", id), "{\"orderStatus\":\"PAID\"}")
            .with(service())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/orders")).andExpect(status().isUnauthorized());
        mockMvc.perform(json(put("/backoffice/orders/{id}/status", id), "{\"orderStatus\":\"PAID\"}").with(csrf()))
            .andExpect(status().isUnauthorized());
        assertThat(statusOf(id)).isEqualTo("PENDING_PAYMENT");
    }
}
