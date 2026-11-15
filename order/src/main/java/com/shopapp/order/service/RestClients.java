package com.shopapp.order.service;

import com.shopapp.commonlibrary.observability.ObservedRestClient;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** RestClients with timeouts, so a slow downstream service cannot hang checkout (decisions.md 2b-3). */
final class RestClients {

    private RestClients() {
    }

    static RestClient withTimeouts(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        return ObservedRestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
