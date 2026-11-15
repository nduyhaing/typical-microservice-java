package com.shopapp.payment.paypal.service;

import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** RestClients with short timeouts, so a slow PayPal cannot hang the payment flow (decisions.md 2b-3, 5-24). */
final class RestClients {

    private RestClients() {
    }

    static RestClient withTimeouts(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
