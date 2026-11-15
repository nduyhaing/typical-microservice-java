package com.shopapp.backofficebff.config;

import java.time.Duration;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Keeps the gateway's upstream connections pointing at the current containers (decisions.md 4-80). Reactor Netty
 * resolves host names with its own DNS client and caches each answer for the record's TTL; Docker's embedded DNS
 * answers with a TTL of 600 s, so after `storefront` / `backoffice` / `nginx` was recreated with a new IP the BFF kept
 * connecting to the old one (500s) for up to 10 minutes or until it was restarted. Answers are now cached 10 s at
 * most; idle pooled connections are dropped after 30 s and every connection after 5 min (application.yaml).
 */
@Configuration(proxyBeanMethods = false)
public class GatewayHttpClientConfig {

    static final Duration DNS_CACHE_MAX_TTL = Duration.ofSeconds(10);

    @Bean
    HttpClientCustomizer shortDnsCacheCustomizer() {
        return httpClient -> httpClient.resolver(spec -> spec
            .cacheMaxTimeToLive(DNS_CACHE_MAX_TTL)
            .cacheNegativeTimeToLive(Duration.ZERO));
    }
}
