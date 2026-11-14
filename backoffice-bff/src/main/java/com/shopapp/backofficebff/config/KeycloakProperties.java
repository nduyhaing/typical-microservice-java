package com.shopapp.backofficebff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keycloak as seen by the BFF. {@code publicUrl} is what the browser reaches (and the token issuer);
 * {@code internalUrl} is used for server-to-server calls, so the BFF never depends on nginx being up.
 */
@ConfigurationProperties(prefix = "shopapp.keycloak")
public record KeycloakProperties(String publicUrl, String internalUrl, String realm, String clientId,
                                 String clientSecret) {

    String publicRealmUrl() {
        return publicUrl + "/realms/" + realm;
    }

    String internalOidcUrl(String endpoint) {
        return internalUrl + "/realms/" + realm + "/protocol/openid-connect/" + endpoint;
    }

    String publicOidcUrl(String endpoint) {
        return publicRealmUrl() + "/protocol/openid-connect/" + endpoint;
    }
}
