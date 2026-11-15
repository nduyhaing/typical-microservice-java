package com.shopapp.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** PayPal: init answers the approval URL; capture is verified against the order before it is marked paid. */
class PaypalPaymentIT extends PaymentItSupport {

    /** Starts a PayPal payment for the order and answers PayPal's order id (the return URL's token). */
    private String initPaypal(long orderId, String customer) throws Exception {
        mockMvc.perform(json(post("/init"), "{\"orderId\":" + orderId + ",\"paymentMethod\":\"PAYPAL\"}")
                .with(customer(customer)))
            .andExpect(status().isOk());
        return "PP" + PAYPAL_ORDER_SEQ.get();
    }

    private org.springframework.test.web.servlet.ResultActions capture(String token, String customer)
        throws Exception {
        return mockMvc.perform(json(post("/capture"), "{\"token\":\"" + token + "\"}").with(customer(customer)));
    }

    @Test
    void initPaypal_answersTheApprovalUrl_andSendsTheOrderAmountWithOurReference() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");

        String body = mockMvc.perform(json(post("/init"),
                "{\"orderId\":10,\"paymentMethod\":\"PAYPAL\",\"totalPrice\":1}").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAYER_ACTION_REQUIRED"))
            .andExpect(jsonPath("$.redirectUrl").value(org.hamcrest.Matchers.startsWith(
                "https://www.sandbox.paypal.com/checkoutnow?token=PP")))
            .andReturn().getResponse().getContentAsString();
        long paymentId = ((Number) JsonPath.read(body, "$.paymentId")).longValue();

        PaymentItSupport.Call call = PAYPAL_CREATE_CALLS.getFirst();
        assertThat(call.authorization()).isEqualTo("Bearer " + SERVICE_TOKEN);
        assertThat(new BigDecimal(JsonPath.read(call.body(), "$.amount").toString())).isEqualByComparingTo("120.50");
        assertThat((String) JsonPath.read(call.body(), "$.reference")).isEqualTo("payment-" + paymentId);
        // the payment's own random PayPal-Request-Id, stored with it (5-67)
        String requestId = JsonPath.read(call.body(), "$.requestId");
        assertThat(java.util.UUID.fromString(requestId)).isNotNull();
        assertThat(jdbc.queryForObject("select paypal_request_id::text from payment where id = ?", String.class,
            paymentId)).isEqualTo(requestId);
        assertThat(jdbc.queryForObject("select payment_provider_checkout_id from payment where id = ?",
            String.class, paymentId)).startsWith("PP");
        assertThat(paymentStatus(paymentId)).isEqualTo("PENDING");
    }

    @Test
    void paypalNotConfigured_initIsA503_andTheProviderIsHidden() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        PAYPAL_CONFIGURED.set(false);

        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"PAYPAL\"}").with(customer("u-1")))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value("PayPal is not configured"));
        mockMvc.perform(get("/storefront/payment-providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("COD"));
    }

    @Test
    void capture_verified_marksTheOrderPaidWithTheOrderAmount() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String token = initPaypal(10, "u-1");

        capture(token, "u-1")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderId").value(10))
            .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.gatewayTransactionId").value("CAP" + token))
            .andExpect(jsonPath("$.paymentFee").value(0.71));

        assertThat(PAYPAL_CAPTURE_CALLS).hasSize(1);
        // order was told a capture is starting (cancelling blocked meanwhile, 5-62) before PayPal was called
        assertThat(ORDER_IN_PROGRESS_CALLS).singleElement()
            .satisfies(call -> assertThat(call.method()).isEqualTo("PUT"));
        assertThat(ORDER_PAY_CALLS).hasSize(1);
        String body = ORDER_PAY_CALLS.getFirst().body();
        assertThat(new BigDecimal(JsonPath.read(body, "$.amount").toString())).isEqualByComparingTo("120.50");
        long paymentId = jdbc.queryForObject("select id from payment where payment_provider_checkout_id = ?",
            Long.class, token);
        assertThat(((Number) JsonPath.read(body, "$.paymentId")).longValue()).isEqualTo(paymentId);
        assertThat(jdbc.queryForObject("select order_confirmed from payment where id = ?", Boolean.class, paymentId))
            .isTrue();
    }

    @Test
    void capturedAmountMismatch_isA400_notPaid_andOrderIsNotCalled() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String token = initPaypal(10, "u-1");
        CAPTURED_AMOUNT.set("1.00");

        capture(token, "u-1").andExpect(status().isBadRequest());

        assertThat(ORDER_PAY_CALLS).isEmpty();
        long paymentId = jdbc.queryForObject("select id from payment where payment_provider_checkout_id = ?",
            Long.class, token);
        assertThat(paymentStatus(paymentId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select failure_message from payment where id = ?", String.class, paymentId))
            .contains("1.00");
    }

    @Test
    void capturedCurrencyOrReferenceMismatch_isA400_andOrderIsNotCalled() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String first = initPaypal(10, "u-1");
        CAPTURED_CURRENCY.set("EUR");
        capture(first, "u-1").andExpect(status().isBadRequest());

        order(11, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String second = initPaypal(11, "u-1");
        CAPTURED_CURRENCY.set("USD");
        // a capture whose custom_id names another payment (e.g. a PayPal order created for another order)
        CAPTURED_CUSTOM_ID.set("payment-999");
        capture(second, "u-1").andExpect(status().isBadRequest());

        assertThat(ORDER_PAY_CALLS).isEmpty();
    }

    @Test
    void replayedCapture_isIdempotent_andNeverCapturesOrPaysTwice() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String token = initPaypal(10, "u-1");

        capture(token, "u-1").andExpect(status().isOk());
        capture(token, "u-1").andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"));

        assertThat(PAYPAL_CAPTURE_CALLS).hasSize(1);
        assertThat(ORDER_PAY_CALLS).hasSize(1);
        assertThat(paymentCount()).isEqualTo(1);
    }

    @Test
    void captureForAnotherUsersOrder_orAnUnknownToken_isA404() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String token = initPaypal(10, "u-1");

        capture(token, "u-2").andExpect(status().isNotFound());
        capture("UNKNOWN123", "u-1").andExpect(status().isNotFound());

        assertThat(PAYPAL_CAPTURE_CALLS).isEmpty();
        assertThat(ORDER_PAY_CALLS).isEmpty();
    }

    @Test
    void captureOfAnOrderCancelledMeanwhile_isA400_andPaypalIsNotCaptured() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String token = initPaypal(10, "u-1");
        order(10, "u-1", "120.50", "CANCELLED", "CANCELLED");

        capture(token, "u-1").andExpect(status().isBadRequest());

        assertThat(PAYPAL_CAPTURE_CALLS).isEmpty();
        assertThat(ORDER_PAY_CALLS).isEmpty();
    }

    @Test
    void captureRefusedByPaypal_isA400_withPaypalsIssue() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String token = initPaypal(10, "u-1");
        PAYPAL_CAPTURE_ANSWER.set(new Answer(400, "{\"statusCode\":\"400 BAD_REQUEST\",\"title\":"
            + "\"ORDER_NOT_APPROVED\",\"detail\":\"PayPal refused the request: ORDER_NOT_APPROVED\",\"fieldErrors\":[]}"));

        capture(token, "u-1")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("ORDER_NOT_APPROVED"));

        assertThat(ORDER_PAY_CALLS).isEmpty();
        long paymentId = jdbc.queryForObject("select id from payment where payment_provider_checkout_id = ?",
            Long.class, token);
        // not approved yet: the buyer may still approve and come back
        assertThat(paymentStatus(paymentId)).isEqualTo("PENDING");
    }

    @Test
    void orderDownAfterCapture_isA503_andAReplayRecordsItWithoutCapturingAgain() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String token = initPaypal(10, "u-1");
        ORDER_PAY_STATUS.set(503);

        capture(token, "u-1").andExpect(status().isServiceUnavailable());
        long paymentId = jdbc.queryForObject("select id from payment where payment_provider_checkout_id = ?",
            Long.class, token);
        assertThat(paymentStatus(paymentId)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select order_confirmed from payment where id = ?", Boolean.class, paymentId))
            .isFalse();

        ORDER_PAY_STATUS.set(200);
        capture(token, "u-1").andExpect(status().isOk());

        assertThat(PAYPAL_CAPTURE_CALLS).hasSize(1);
        assertThat(ORDER_PAY_CALLS).hasSize(2);
        assertThat(jdbc.queryForObject("select order_confirmed from payment where id = ?", Boolean.class, paymentId))
            .isTrue();
    }

    @Test
    void cancelReturn_cancelsTheBuyersPendingPaypalPayment_only() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String token = initPaypal(10, "u-1");

        mockMvc.perform(json(post("/cancel"), "{\"token\":\"" + token + "\"}").with(customer("u-2")))
            .andExpect(status().isNotFound());
        // a state change is never a GET (5-68)
        mockMvc.perform(get("/cancel").param("token", token).with(customer("u-1")))
            .andExpect(status().is4xxClientError());
        assertThat(paymentStatus(paymentIdOf(token))).isEqualTo("PENDING");
        mockMvc.perform(json(post("/cancel"), "{\"token\":\"" + token + "\"}").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentStatus").value("CANCELLED"));
        mockMvc.perform(json(post("/cancel"), "{\"token\":\"bad/token\"}").with(customer("u-1")))
            .andExpect(status().isBadRequest());

        capture(token, "u-1").andExpect(status().isBadRequest());
        assertThat(PAYPAL_CAPTURE_CALLS).isEmpty();
    }

    @Test
    void codAfterAnAbandonedPaypalAttempt_cancelsThePaypalPayment() throws Exception {
        order(10, "u-1", "120.50", "PENDING_PAYMENT", "PENDING");
        String token = initPaypal(10, "u-1");

        mockMvc.perform(json(post("/init"), "{\"orderId\":10,\"paymentMethod\":\"COD\"}").with(customer("u-1")))
            .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("select payment_status from payment where payment_provider_checkout_id = ?",
            String.class, token)).isEqualTo("CANCELLED");
        capture(token, "u-1").andExpect(status().isBadRequest());
        assertThat(PAYPAL_CAPTURE_CALLS).isEmpty();
    }
}
