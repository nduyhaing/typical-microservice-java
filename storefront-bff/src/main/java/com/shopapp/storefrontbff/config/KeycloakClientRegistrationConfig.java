package com.shopapp.storefrontbff.config;

import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * The Keycloak login client, built explicitly instead of from an issuer URI: Spring would fetch the issuer's
 * metadata at startup through the public hostname, which is nginx — and nginx only starts after the BFF is
 * healthy (decisions.md 2b-6). Browser-facing endpoints use the public URL, back-channel ones the internal URL.
 */
@Configuration
@EnableConfigurationProperties(KeycloakProperties.class)
public class KeycloakClientRegistrationConfig {

    public static final String REGISTRATION_ID = "keycloak";

    @Bean
    public ReactiveClientRegistrationRepository clientRegistrationRepository(KeycloakProperties keycloak) {
        ClientRegistration registration = ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId(keycloak.clientId())
            .clientSecret(keycloak.clientSecret())
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email", "roles")
            .issuerUri(keycloak.publicRealmUrl())
            .authorizationUri(keycloak.publicOidcUrl("auth"))
            .tokenUri(keycloak.internalOidcUrl("token"))
            .jwkSetUri(keycloak.internalOidcUrl("certs"))
            .userInfoUri(keycloak.internalOidcUrl("userinfo"))
            .userNameAttributeName("preferred_username")
            .providerConfigurationMetadata(Map.of("end_session_endpoint", keycloak.publicOidcUrl("logout")))
            .clientName("Keycloak")
            .build();
        return new InMemoryReactiveClientRegistrationRepository(registration);
    }
}
