package com.shopapp.customer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keycloak admin API connection (YAS keys: keycloak.auth-server-url, realm, resource, credentials.secret).
 */
@ConfigurationProperties(prefix = "keycloak")
public record KeycloakPropsConfig(String authServerUrl, String realm, String resource, Credentials credentials) {

    public record Credentials(String secret) {
    }
}
