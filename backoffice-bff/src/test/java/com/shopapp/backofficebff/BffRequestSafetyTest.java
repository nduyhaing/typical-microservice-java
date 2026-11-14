package com.shopapp.backofficebff;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Login;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * Failure modes around the admin's BFF session: an expired Keycloak session and cross-site write requests.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(RedisContainerSupport.class)
class BffRequestSafetyTest {

    @Autowired
    private WebTestClient client;

    /** Stands in for Keycloak refusing to refresh the admin's token (SSO session ended). */
    @MockitoBean
    private ReactiveOAuth2AuthorizedClientManager authorizedClientManager;

    @Test
    void apiCall_whenTokenCanNoLongerBeRefreshed_returns401JsonNotRedirectOr500() {
        when(authorizedClientManager.authorize(any())).thenReturn(Mono.error(
            new ClientAuthorizationException(new OAuth2Error("invalid_grant", "Session not active", null), "keycloak")));

        client.mutateWith(mockOAuth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .get().uri("/api/product/backoffice/brands").exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.statusCode").isEqualTo("401 UNAUTHORIZED")
            .jsonPath("$.detail").isEqualTo("Your session has expired. Please log in again.");
    }

    @Test
    void crossSitePost_isRejected() {
        client.mutateWith(mockOAuth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .post().uri("/api/product/backoffice/brands").header("Origin", "http://evil.example").exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void siblingSubdomainPost_isRejectedBySecFetchSite() {
        client.post().uri("/logout").header("Sec-Fetch-Site", "same-site").exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void sameOriginPost_isAllowed() {
        // absolute URI: the mock environment otherwise has no scheme/host to compare the Origin with
        client.post().uri("http://localhost/logout").header("Origin", "http://localhost")
            .header("Sec-Fetch-Site", "same-origin")
            .exchange()
            .expectStatus().is3xxRedirection();
    }
}
