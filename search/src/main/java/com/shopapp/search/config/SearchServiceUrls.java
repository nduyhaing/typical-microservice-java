package com.shopapp.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URL of the product service (straight to the container in docker compose). */
@ConfigurationProperties(prefix = "shopapp.services")
public record SearchServiceUrls(String product) {
}
