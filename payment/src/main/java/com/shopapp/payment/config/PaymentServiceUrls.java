package com.shopapp.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Base URLs of the services payment calls (straight to the containers in docker compose; decisions.md 5-11). */
@ConfigurationProperties(prefix = "shopapp.services")
public record PaymentServiceUrls(String order, String paymentPaypal) {
}
