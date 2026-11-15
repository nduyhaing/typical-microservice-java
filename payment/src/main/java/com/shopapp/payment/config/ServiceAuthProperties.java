package com.shopapp.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The payment service's own Keycloak client (confidential client {@code payment-service}, realm role
 * {@code SERVICE}), used with the client-credentials grant for order's and payment-paypal's internal endpoints
 * (decisions.md 5-11; same scheme as the order service, 5-5).
 *
 * @param tokenUri the realm's token endpoint (in compose straight to Keycloak on the network)
 */
@ConfigurationProperties(prefix = "shopapp.service-auth")
public record ServiceAuthProperties(String tokenUri, String clientId, String clientSecret) {
}
