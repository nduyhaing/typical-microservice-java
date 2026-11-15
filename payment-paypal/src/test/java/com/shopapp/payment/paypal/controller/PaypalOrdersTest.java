package com.shopapp.payment.paypal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.payment.paypal.service.PaypalTokenProvider;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** payment-paypal against a JDK HttpServer PayPal stub (no Docker needed). */
@SpringBootTest
@AutoConfigureMockMvc
class PaypalOrdersTest {

    private static final String REQUEST_ID = "7d0c4e9a-3f1b-4c2a-9e8d-1a2b3c4d5e6f";
    private static final String CREATE = "{\"amount\":12.34,\"reference\":\"payment-7\",\"requestId\":\""
        + REQUEST_ID + "\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaypalTokenProvider tokens;

    @DynamicPropertySource
    static void paypal(DynamicPropertyRegistry registry) {
        registry.add("shopapp.paypal.base-url", PaypalStub::baseUrl);
        registry.add("shopapp.paypal.client-id", () -> "client-1");
        registry.add("shopapp.paypal.client-secret", () -> "secret-1");
        registry.add("shopapp.paypal.return-url", () -> "http://storefront.test/checkout/payment-return");
        registry.add("shopapp.paypal.cancel-url", () -> "http://storefront.test/checkout/payment-return?cancelled=true");
    }

    @BeforeEach
    void reset() {
        PaypalStub.reset();
        tokens.evict();
    }

    static RequestPostProcessor service() {
        return jwt().jwt(token -> token.subject("service-account-payment-service"))
            .authorities(new SimpleGrantedAuthority("ROLE_SERVICE_PAYPAL"));
    }

    private org.springframework.test.web.servlet.ResultActions create(String body) throws Exception {
        return mockMvc.perform(post("/internal/orders").with(service())
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void create_sendsAmountCurrencyReferenceAndReturnUrls_andAnswersTheApprovalUrl() throws Exception {
        create(CREATE)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paypalOrderId").value("5O190127TN364715T"))
            .andExpect(jsonPath("$.status").value("PAYER_ACTION_REQUIRED"))
            .andExpect(jsonPath("$.approvalUrl")
                .value("https://www.sandbox.paypal.com/checkoutnow?token=5O190127TN364715T"));

        assertThat(PaypalStub.TOKEN_CALLS).hasSize(1);
        PaypalStub.Call tokenCall = PaypalStub.TOKEN_CALLS.getFirst();
        assertThat(tokenCall.authorization()).isEqualTo("Basic "
            + Base64.getEncoder().encodeToString("client-1:secret-1".getBytes(StandardCharsets.UTF_8)));
        assertThat(tokenCall.body()).isEqualTo("grant_type=client_credentials");

        PaypalStub.Call call = PaypalStub.CREATE_CALLS.getFirst();
        assertThat(call.authorization()).startsWith("Bearer paypal-token-");
        assertThat(call.requestId()).isEqualTo(REQUEST_ID);
        assertThat((String) JsonPath.read(call.body(), "$.intent")).isEqualTo("CAPTURE");
        assertThat((String) JsonPath.read(call.body(), "$.purchase_units[0].amount.currency_code")).isEqualTo("USD");
        assertThat((String) JsonPath.read(call.body(), "$.purchase_units[0].amount.value")).isEqualTo("12.34");
        assertThat((String) JsonPath.read(call.body(), "$.purchase_units[0].custom_id")).isEqualTo("payment-7");
        assertThat((String) JsonPath.read(call.body(), "$.purchase_units[0].reference_id")).isEqualTo("payment-7");
        assertThat((String) JsonPath.read(call.body(), "$.payment_source.paypal.experience_context.return_url"))
            .isEqualTo("http://storefront.test/checkout/payment-return");
        assertThat((String) JsonPath.read(call.body(), "$.payment_source.paypal.experience_context.cancel_url"))
            .isEqualTo("http://storefront.test/checkout/payment-return?cancelled=true");
    }

    @Test
    void amount_isSentWithTwoDecimals_andNeverCapped() throws Exception {
        create("{\"amount\":2500,\"reference\":\"payment-8\",\"requestId\":\"" + UUID.randomUUID() + "\"}")
            .andExpect(status().isOk());

        // YAS capped the PayPal amount at 1000 — a capture would then never match the order total
        assertThat((String) JsonPath.read(PaypalStub.CREATE_CALLS.getFirst().body(),
            "$.purchase_units[0].amount.value")).isEqualTo("2500.00");
    }

    @Test
    void token_isCached_acrossCalls() throws Exception {
        create(CREATE).andExpect(status().isOk());
        create("{\"amount\":1.00,\"reference\":\"payment-9\",\"requestId\":\"" + UUID.randomUUID() + "\"}")
            .andExpect(status().isOk());
        mockMvc.perform(post("/internal/orders/5O190127TN364715T/capture").with(service()))
            .andExpect(status().isOk());

        assertThat(PaypalStub.TOKEN_CALLS).hasSize(1);
        String first = PaypalStub.CREATE_CALLS.getFirst().authorization();
        assertThat(PaypalStub.CREATE_CALLS.get(1).authorization()).isEqualTo(first);
        assertThat(PaypalStub.CAPTURE_CALLS.getFirst().authorization()).isEqualTo(first);
    }

    @Test
    void expiredToken_401_getsANewTokenAndRetriesOnce() throws Exception {
        create(CREATE).andExpect(status().isOk());
        PaypalStub.CREATE_UNAUTHORIZED.set(1);

        create(CREATE).andExpect(status().isOk());

        assertThat(PaypalStub.TOKEN_CALLS).hasSize(2);
        assertThat(PaypalStub.CREATE_CALLS).hasSize(3);
        assertThat(PaypalStub.CREATE_CALLS.get(2).authorization())
            .isNotEqualTo(PaypalStub.CREATE_CALLS.get(1).authorization());
    }

    @Test
    void repeated401_isA502_notALoop() throws Exception {
        PaypalStub.CREATE_UNAUTHORIZED.set(5);

        create(CREATE).andExpect(status().isBadGateway());

        assertThat(PaypalStub.CREATE_CALLS).hasSize(2);
    }

    @Test
    void capture_answersAmountCurrencyCustomIdAndCaptureId() throws Exception {
        mockMvc.perform(post("/internal/orders/5O190127TN364715T/capture").with(service()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paypalOrderId").value("5O190127TN364715T"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.captureId").value("CAPTURE-5O190127TN364715T"))
            .andExpect(jsonPath("$.captureStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.amount").value(12.34))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andExpect(jsonPath("$.customId").value("payment-7"))
            .andExpect(jsonPath("$.paymentFee").value(0.71));

        PaypalStub.Call call = PaypalStub.CAPTURE_CALLS.getFirst();
        assertThat(call.method()).isEqualTo("POST");
        assertThat(call.requestId()).isEqualTo("capture-5O190127TN364715T");
    }

    @Test
    void capture_refusedByPaypal_isA400WithPaypalsIssue() throws Exception {
        PaypalStub.CAPTURE_ANSWER.set(new PaypalStub.Answer(422, """
            {"name":"UNPROCESSABLE_ENTITY","details":[{"issue":"ORDER_NOT_APPROVED",
             "description":"Payer has not yet approved the Order for payment."}],"message":"x"}"""));

        mockMvc.perform(post("/internal/orders/5O190127TN364715T/capture").with(service()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("ORDER_NOT_APPROVED"));
    }

    @Test
    void capture_alreadyCapturedAtPaypal_readsThePaypalOrder_andAnswersItsCapture() throws Exception {
        PaypalStub.CAPTURE_ANSWER.set(new PaypalStub.Answer(422, """
            {"name":"UNPROCESSABLE_ENTITY","details":[{"issue":"ORDER_ALREADY_CAPTURED",
             "description":"Order already captured."}],"message":"x"}"""));

        mockMvc.perform(post("/internal/orders/5O190127TN364715T/capture").with(service()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.captureId").value("CAPTURE-5O190127TN364715T"))
            .andExpect(jsonPath("$.captureStatus").value("COMPLETED"))
            .andExpect(jsonPath("$.amount").value(12.34))
            .andExpect(jsonPath("$.customId").value("payment-7"));

        assertThat(PaypalStub.GET_CALLS).singleElement()
            .satisfies(call -> assertThat(call.path()).isEqualTo("/v2/checkout/orders/5O190127TN364715T"));
    }

    @Test
    void capture_alreadyCaptured_butThePaypalOrderHasNoCompletedCapture_isA400() throws Exception {
        PaypalStub.CAPTURE_ANSWER.set(new PaypalStub.Answer(422, """
            {"name":"UNPROCESSABLE_ENTITY","details":[{"issue":"ORDER_ALREADY_CAPTURED"}],"message":"x"}"""));
        PaypalStub.GET_ANSWER.set(new PaypalStub.Answer(200, "{\"id\":\"5O190127TN364715T\","
            + "\"status\":\"APPROVED\",\"purchase_units\":[{\"reference_id\":\"payment-7\"}]}"));

        mockMvc.perform(post("/internal/orders/5O190127TN364715T/capture").with(service()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("ORDER_ALREADY_CAPTURED"));
    }

    @Test
    void capture_ofAnUnknownOrder_isA404() throws Exception {
        PaypalStub.CAPTURE_ANSWER.set(new PaypalStub.Answer(404, """
            {"name":"RESOURCE_NOT_FOUND","details":[{"issue":"INVALID_RESOURCE_ID"}],"message":"x"}"""));

        mockMvc.perform(post("/internal/orders/UNKNOWN1/capture").with(service()))
            .andExpect(status().isNotFound());
    }

    @Test
    void paypalDown_isA502() throws Exception {
        PaypalStub.CREATE_ANSWER.set(new PaypalStub.Answer(500, "{\"name\":\"INTERNAL_SERVER_ERROR\"}"));

        create(CREATE).andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.detail").value("PayPal is unavailable, please try again later"));
    }

    @Test
    void createdOrderWithoutApprovalLink_isA502() throws Exception {
        PaypalStub.CREATE_ANSWER.set(new PaypalStub.Answer(200, "{\"id\":\"X1\",\"status\":\"CREATED\",\"links\":[]}"));

        create(CREATE).andExpect(status().isBadGateway());
    }

    @Test
    void invalidRequests_areRefused_beforePaypalIsCalled() throws Exception {
        create("{\"amount\":0,\"reference\":\"payment-7\"}").andExpect(status().isBadRequest());
        create("{\"amount\":1.234,\"reference\":\"payment-7\"}").andExpect(status().isBadRequest());
        create("{\"amount\":1,\"reference\":\"\"}").andExpect(status().isBadRequest());
        create("{\"amount\":1,\"reference\":\"a/b?c\"}").andExpect(status().isBadRequest());
        // the payment's own PayPal request id is required (5-67)
        create("{\"amount\":1,\"reference\":\"payment-7\"}").andExpect(status().isBadRequest());
        create("{\"amount\":1,\"reference\":\"payment-7\",\"requestId\":\"not-a-uuid\"}")
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/internal/orders/bad%2Fid/capture").with(service()))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/internal/orders/" + "A".repeat(65) + "/capture").with(service()))
            .andExpect(status().isBadRequest());

        assertThat(PaypalStub.CREATE_CALLS).isEmpty();
        assertThat(PaypalStub.CAPTURE_CALLS).isEmpty();
    }

    @Test
    void status_saysConfiguredAndUsd() throws Exception {
        mockMvc.perform(get("/internal/status").with(service()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(true))
            .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void onlyTheServiceRole_mayCall() throws Exception {
        mockMvc.perform(get("/internal/status")).andExpect(status().isUnauthorized());
        // anonymous POST: refused before authentication (no bearer token, so CSRF applies)
        mockMvc.perform(post("/internal/orders").contentType(MediaType.APPLICATION_JSON).content(CREATE))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/internal/orders").contentType(MediaType.APPLICATION_JSON).content(CREATE)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/internal/orders").contentType(MediaType.APPLICATION_JSON).content(CREATE)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/internal/status").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .andExpect(status().isForbidden());
        // another service's capability (the order service's) and the old catch-all SERVICE role (5-66)
        for (String role : java.util.List.of("ROLE_SERVICE_GOODS_BACK", "ROLE_SERVICE", "ROLE_SERVICE_ORDER_PAYMENT")) {
            mockMvc.perform(post("/internal/orders").contentType(MediaType.APPLICATION_JSON).content(CREATE)
                    .with(jwt().authorities(new SimpleGrantedAuthority(role))))
                .andExpect(status().isForbidden());
        }

        assertThat(PaypalStub.CREATE_CALLS).isEmpty();
    }
}
