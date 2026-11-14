package com.shopapp.customer.config;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakClientConfig {

    /** Admin client authenticated with the customer-management service account (client credentials). */
    @Bean
    public Keycloak keycloak(KeycloakPropsConfig props) {
        return KeycloakBuilder.builder()
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
            .serverUrl(props.authServerUrl())
            .realm(props.realm())
            .clientId(props.resource())
            .clientSecret(props.credentials().secret())
            .build();
    }
}
