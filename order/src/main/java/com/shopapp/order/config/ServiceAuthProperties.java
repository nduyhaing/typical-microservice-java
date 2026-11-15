package com.shopapp.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The order service's own Keycloak client (confidential client {@code order-service}, realm role {@code SERVICE}),
 * used with the client-credentials grant to call the internal endpoints of product and promotion that give an order's
 * stock and coupon use back (decisions.md 5-4, 5-5).
 *
 * @param tokenUri the realm's token endpoint (in compose straight to Keycloak on the network)
 */
@ConfigurationProperties(prefix = "shopapp.service-auth")
public record ServiceAuthProperties(String tokenUri, String clientId, String clientSecret) {
}
