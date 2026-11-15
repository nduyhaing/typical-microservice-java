package com.shopapp.inventory.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URLs of the services inventory calls (straight to the containers in docker compose). */
@ConfigurationProperties(prefix = "shopapp.services")
public record InventoryServiceUrls(String product, String location) {
}
