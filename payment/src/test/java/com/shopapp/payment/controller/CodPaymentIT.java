package com.shopapp.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Cash on delivery: init with the amount from order, owner checks, admin confirmation marks the order paid. */
class CodPaymentIT extends PaymentItSupport {

    private long initCod(long orderId, String customer) throws Exception {
        String body = mockMvc.perform(json(post("/init"), "{\"orderId\":" + orderId + ",\"paymentMethod\":\"COD\"}")
                .with(customer(customer)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.paymentId")).longValue();
    }

    @Test
    void initCod_forOwnOrder_recordsAPendingPaymentWithTheOrdersAmount_notTheBodys() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");

        String body = mockMvc.perform(json(post("/init"),
                "{\"orderId\":10,\"paymentMethod\":\"COD\",\"totalPrice\":0.01,\"amount\":0.01}")
                .with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.redirectUrl").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long paymentId = ((Number) JsonPath.read(body, "$.paymentId")).longValue();

        assertThat(paymentAmount(paymentId)).isEqualByComparingTo("120.50");
        assertThat(paymentStatus(paymentId)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("select customer_id from payment where id = ?", String.class, paymentId))
            .isEqualTo("u-1");
        // order is asked with the payment service's own token, never the customer's
        assertThat(ORDER_GET_CALLS).allSatisfy(call ->
            assertThat(call.authorization()).isEqualTo("Bearer " + SERVICE_TOKEN));
        assertThat(ORDER_PAY_CALLS).isEmpty();
    }

    @Test
    void initCod_twice_answersTheSamePendingPayment() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");

        long first = initCod(10, "u-1");
        long second = initCod(10, "u-1");

        assertThat(second).isEqualTo(first);
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void anotherUsersOrder_isA404() throws Exception {
        order(10, "u-2", "120.50", "PENDING_PAYMENT", "PENDING");

        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"COD\"}").with(customer("u-1")))
            .andExpect(status().isNotFound());
        mockMvc.perform(json(post("/init"), "{\"orderId\":99,\"paymentMethod\":\"COD\"}").with(customer("u-1")))
            .andExpect(status().isNotFound());

        assertThat(paymentCount()).isZero();
    }

    @Test
    void paidOrder_isA400() throws Exception {
        order(10, "u-1", "120.50", "PAID", "COMPLETED");

        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"COD\"}").with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Order 10 is already paid"));
        assertThat(paymentCount()).isZero();
    }

    @Test
    void cancelledOrRejectedOrRefundedOrder_isA400() throws Exception {
        order(10, "u-1", "120.50", "CANCELLED", "CANCELLED");
        order(11, "u-1", "120.50", "REJECT", "PENDING");
        order(12, "u-1", "120.50", "REFUND", "COMPLETED");

        for (long id = 10; id <= 12; id++) {
            mockMvc.perform(json(post("/init"), "{\"orderId\":" + id + ",\"paymentMethod\":\"COD\"}")
                    .with(customer("u-1")))
                .andExpect(status().isBadRequest());
        }
        assertThat(paymentCount()).isZero();
    }

    @Test
    void disabledOrUnhandledMethod_isA400() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        jdbc.update("update payment_provider set enabled = false where id = 'COD'");

        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"COD\"}").with(customer("u-1")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"BANKING\"}").with(customer("u-1")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"BITCOIN\"}").with(customer("u-1")))
            .andExpect(status().isBadRequest());
        assertThat(paymentCount()).isZero();
    }

    @Test
    void init_needsTheCustomerRole() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");

        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"COD\"}").with(admin()))
            .andExpect(status().isForbidden());
        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"COD\"}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void orderDown_isA503_andNothingIsRecorded() throws Exception {
        ORDER_DOWN.set(true);

        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"COD\"}").with(customer("u-1")))
            .andExpect(status().isServiceUnavailable());
        assertThat(paymentCount()).isZero();
    }

    @Test
    void adminConfirm_callsOrderWithTheOrderAmountAndPaymentId_andCompletesThePayment() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        long paymentId = initCod(10, "u-1");

        mockMvc.perform(put("/backoffice/payments/" + paymentId + "/confirm").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.orderConfirmed").value(true));

        assertThat(ORDER_PAY_CALLS).hasSize(1);
        PaymentItSupport.Call call = ORDER_PAY_CALLS.getFirst();
        assertThat(call.method()).isEqualTo("PUT");
        assertThat(call.path()).isEqualTo("/order/internal/orders/10/payment");
        assertThat(call.authorization()).isEqualTo("Bearer " + SERVICE_TOKEN);
        assertThat(((Number) JsonPath.read(call.body(), "$.paymentId")).longValue()).isEqualTo(paymentId);
        assertThat(new BigDecimal(JsonPath.read(call.body(), "$.amount").toString())).isEqualByComparingTo("120.50");
        assertThat(paymentStatus(paymentId)).isEqualTo("COMPLETED");
    }

    @Test
    void adminConfirm_twice_isIdempotent() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        long paymentId = initCod(10, "u-1");

        mockMvc.perform(put("/backoffice/payments/" + paymentId + "/confirm").with(admin()))
            .andExpect(status().isOk());
        mockMvc.perform(put("/backoffice/payments/" + paymentId + "/confirm").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"));

        assertThat(ORDER_PAY_CALLS).hasSize(1);
    }

    @Test
    void adminConfirm_refusedByOrder_isA400_andThePaymentStaysPending() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        long paymentId = initCod(10, "u-1");
        ORDER_PAY_STATUS.set(400);

        mockMvc.perform(put("/backoffice/payments/" + paymentId + "/confirm").with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Order 10 is already paid"));
        assertThat(paymentStatus(paymentId)).isEqualTo("PENDING");
    }

    @Test
    void adminConfirm_orderDown_isA503_andThePaymentStaysPending() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        long paymentId = initCod(10, "u-1");
        ORDER_DOWN.set(true);

        mockMvc.perform(put("/backoffice/payments/" + paymentId + "/confirm").with(admin()))
            .andExpect(status().isServiceUnavailable());
        assertThat(paymentStatus(paymentId)).isEqualTo("PENDING");
    }

    @Test
    void adminConfirm_ofACancelledOrUnknownPayment_isRefused() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        long paymentId = initCod(10, "u-1");
        jdbc.update("update payment set payment_status = 'CANCELLED' where id = ?", paymentId);

        mockMvc.perform(put("/backoffice/payments/" + paymentId + "/confirm").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/backoffice/payments/999/confirm").with(admin()))
            .andExpect(status().isNotFound());
        assertThat(ORDER_PAY_CALLS).isEmpty();
    }

    @Test
    void confirmAndList_areAdminOnly() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        long paymentId = initCod(10, "u-1");

        mockMvc.perform(put("/backoffice/payments/" + paymentId + "/confirm").with(customer("u-1")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/payments").with(customer("u-1")))
            .andExpect(status().isForbidden());
        assertThat(ORDER_PAY_CALLS).isEmpty();
    }

    @Test
    void adminList_filtersAndPages_withBounds() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        order(11, "u-1", "5.00", "PENDING_PAYMENT", "PENDING");
        long first = initCod(10, "u-1");
        initCod(11, "u-1");
        mockMvc.perform(put("/backoffice/payments/" + first + "/confirm").with(admin())).andExpect(status().isOk());

        mockMvc.perform(get("/backoffice/payments").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.payments[0].orderId").value(11));
        mockMvc.perform(get("/backoffice/payments").param("paymentStatus", "COMPLETED").with(admin()))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.payments[0].id").value(first))
            .andExpect(jsonPath("$.payments[0].amount").value(120.5));
        mockMvc.perform(get("/backoffice/payments").param("orderId", "11").param("paymentMethod", "COD")
                .with(admin()))
            .andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/backoffice/payments").param("pageSize", "1").param("pageNo", "1").with(admin()))
            .andExpect(jsonPath("$.payments.length()").value(1))
            .andExpect(jsonPath("$.isLast").value(true));
        mockMvc.perform(get("/backoffice/payments").param("pageSize", "101").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/payments").param("pageNo", "-1").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/payments").param("paymentStatus", "NOPE").with(admin()))
            .andExpect(status().isBadRequest());
    }
}
