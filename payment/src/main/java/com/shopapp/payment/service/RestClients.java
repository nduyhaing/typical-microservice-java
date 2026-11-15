package com.shopapp.payment.service;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** RestClients with short timeouts, so a slow downstream service cannot hang a payment (decisions.md 2b-3). */
final class RestClients {

    private RestClients() {
    }

    /** Order and Keycloak: 2 s connect, 5 s read. */
    static RestClient withTimeouts(String baseUrl) {
        return withTimeouts(baseUrl, Duration.ofSeconds(5));
    }

    /** payment-paypal waits up to 15 s for PayPal itself: 20 s read. */
    static RestClient withTimeouts(String baseUrl, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(readTimeout);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
