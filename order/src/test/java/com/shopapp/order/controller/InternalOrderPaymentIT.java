package com.shopapp.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * The payment service's internal order endpoints (decisions.md 5-6): realm role SERVICE only; the paid amount must
 * equal the order total; idempotent per payment id; an ended order is not payable.
 */
class InternalOrderPaymentIT extends OrderItSupport {

    private static final String ONE_PHONE = "[{\"productId\":25,\"quantity\":1}]";

    private static MockHttpServletRequestBuilder pay(long id, long paymentId, String amount) {
        return json(put("/internal/orders/{id}/payment", id),
            "{\"paymentId\":" + paymentId + ",\"amount\":" + amount + "}");
    }

    private ResultActions payAsService(long id, long paymentId, String amount) throws Exception {
        return mockMvc.perform(pay(id, paymentId, amount).with(service()));
    }

    private String statusOf(long id) {
        return jdbc.queryForObject("select status from \"order\" where id = ?", String.class, id);
    }

    @Test
    void info_givesOwnerAmountAndPaymentState() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        mockMvc.perform(get("/internal/orders/{id}", id).with(service()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(id))
            .andExpect(jsonPath("$.customerId").value("u-1"))
            .andExpect(jsonPath("$.totalPrice").value(819.0))
            .andExpect(jsonPath("$.orderStatus").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
            .andExpect(jsonPath("$.paymentMethod").value("COD"));
        mockMvc.perform(get("/internal/orders/{id}", 999_999).with(service())).andExpect(status().isNotFound());
    }

    @Test
    void payment_withTheOrderTotal_marksItPaid_onceForThatPayment() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        payAsService(id, 7, "819.00").andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("PAID"))
            .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.paymentId").value(7));
        // the same payment again (a retried call): same answer
        payAsService(id, 7, "819").andExpect(status().isOk()).andExpect(jsonPath("$.orderStatus").value("PAID"));
        // another payment for a paid order is refused
        payAsService(id, 8, "819.00").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Order " + id + " is already paid"));
        assertThat(jdbc.queryForObject("select payment_id from \"order\" where id = ?", Long.class, id)).isEqualTo(7L);
    }

    @Test
    void wrongAmount_isRefused_andTheOrderStaysUnpaid() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        payAsService(id, 7, "818.99").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("The amount 818.99 does not match the total of order " + id));
        payAsService(id, 7, "1").andExpect(status().isBadRequest());
        payAsService(id, 7, "-819").andExpect(status().isBadRequest());
        payAsService(id, 7, "819.001").andExpect(status().isBadRequest());
        assertThat(statusOf(id)).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    void endedOrder_isNotPayable() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");
        jdbc.update("update \"order\" set status = 'CANCELLED', payment_status = 'CANCELLED' where id = ?", id);

        payAsService(id, 7, "819.00").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Order " + id + " cannot be paid (status CANCELLED)"));
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        payAsService(999_999, 7, "1").andExpect(status().isNotFound());
    }

    @Test
    void cashOnDeliveryPaidOnDelivery_keepsTheShippingStatus() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");
        jdbc.update("update \"order\" set status = 'SHIPPING' where id = ?", id);

        payAsService(id, 9, "819.00").andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("SHIPPING"))
            .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"));
    }

    @Test
    void onlyTheServiceRole_mayCall() throws Exception {
        long id = placedOrder("u-1", ONE_PHONE, "");

        mockMvc.perform(pay(id, 7, "819.00").with(customer("u-1"))).andExpect(status().isForbidden());
        mockMvc.perform(pay(id, 7, "819.00").with(admin())).andExpect(status().isForbidden());
        mockMvc.perform(pay(id, 7, "819.00").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/internal/orders/{id}", id).with(customer("u-1"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/orders/{id}", id).with(admin())).andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/orders/{id}", id)).andExpect(status().isUnauthorized());
        assertThat(statusOf(id)).isEqualTo("PENDING_PAYMENT");
    }
}
