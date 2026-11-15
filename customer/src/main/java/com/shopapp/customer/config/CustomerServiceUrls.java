package com.shopapp.customer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URLs of the services the customer service calls (straight to the container in docker compose). */
@ConfigurationProperties(prefix = "shopapp.services")
public record CustomerServiceUrls(String location) {
}
