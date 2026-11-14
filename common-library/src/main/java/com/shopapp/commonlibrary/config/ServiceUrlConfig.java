package com.shopapp.commonlibrary.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shopapp.services")
public record ServiceUrlConfig(String media, String product) {
}
