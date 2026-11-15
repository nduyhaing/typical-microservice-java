package com.shopapp.rating.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URLs of the services rating calls (straight to the containers in docker compose). */
@ConfigurationProperties(prefix = "shopapp.services")
public record RatingServiceUrls(String order, String product) {
}
