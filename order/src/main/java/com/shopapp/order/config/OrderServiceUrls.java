package com.shopapp.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URLs of the services the order service calls (through nginx in docker compose). */
@ConfigurationProperties(prefix = "shopapp.services")
public record OrderServiceUrls(String product, String cart) {
}
