package com.shopapp.payment.paypal.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

/** Without PayPal credentials (the default in .env) every call answers 503 and PayPal is never called. */
@SpringBootTest
@AutoConfigureMockMvc
class PaypalNotConfiguredTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void paypal(DynamicPropertyRegistry registry) {
        registry.add("shopapp.paypal.base-url", PaypalStub::baseUrl);
        registry.add("shopapp.paypal.client-id", () -> "");
        registry.add("shopapp.paypal.client-secret", () -> "");
    }

    @BeforeEach
    void reset() {
        PaypalStub.reset();
    }

    private static RequestPostProcessor service() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_SERVICE_PAYPAL"));
    }

    @Test
    void create_andCapture_are503() throws Exception {
        mockMvc.perform(post("/internal/orders").with(service()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":12.34,\"reference\":\"payment-7\","
                    + "\"requestId\":\"7d0c4e9a-3f1b-4c2a-9e8d-1a2b3c4d5e6f\"}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value("PayPal is not configured"));
        mockMvc.perform(post("/internal/orders/5O190127TN364715T/capture").with(service()))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.detail").value("PayPal is not configured"));

        assertThat(PaypalStub.TOKEN_CALLS).isEmpty();
        assertThat(PaypalStub.CREATE_CALLS).isEmpty();
        assertThat(PaypalStub.CAPTURE_CALLS).isEmpty();
    }

    @Test
    void status_saysNotConfigured() throws Exception {
        mockMvc.perform(get("/internal/status").with(service()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.configured").value(false));
    }
}
