package com.shopapp.backofficebff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The whole back office is for admins only (YAS): anonymous users are sent to the Keycloak login, logged-in
 * non-admins get 403, admins get through. Only the health endpoint is open.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(RedisContainerSupport.class)
class AdminAccessTest {

    @Autowired
    private WebTestClient client;

    @Test
    void anonymous_page_redirectsToLogin() {
        client.get().uri("/catalog/brands").exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().location("/oauth2/authorization/keycloak");
    }

    @Test
    void anonymous_authentication_redirectsToLogin() {
        client.get().uri("/authentication").exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().location("/oauth2/authorization/keycloak");
    }

    @Test
    void anonymous_apiGet_isUnauthorizedJson() {
        client.get().uri("/api/product/backoffice/brands").exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.statusCode").isEqualTo("401 UNAUTHORIZED")
            .jsonPath("$.title").isEqualTo("Unauthorized")
            .jsonPath("$.detail").isEqualTo("Your session has expired. Please log in again.")
            .jsonPath("$.fieldErrors").isEmpty();
    }

    @Test
    void anonymous_sameOriginApiPost_isUnauthorizedJson() {
        client.post().uri("http://localhost/api/product/backoffice/brands")
            .header("Origin", "http://localhost").header("Sec-Fetch-Site", "same-origin")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{\"name\":\"x\",\"slug\":\"x\"}")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.detail").isEqualTo("Your session has expired. Please log in again.");
    }

    @Test
    void loginEntryPoint_redirectsToKeycloakWithBackofficeClient() {
        client.get().uri("/oauth2/authorization/keycloak").exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().value("Location", location -> assertThat(location)
                .startsWith("http://identity.shopapp.local/realms/shopapp/protocol/openid-connect/auth")
                .contains("client_id=backoffice-bff"));
    }

    @Test
    void health_isOpen() {
        client.get().uri("/actuator/health").exchange()
            .expectStatus().isOk();
    }

    @Test
    void loggedInNonAdmin_authentication_isForbidden() {
        client.mutateWith(mockOidcLogin()
                .idToken(token -> token.claim("preferred_username", "customer"))
                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
            .get().uri("/authentication").exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void loggedInNonAdmin_api_isForbidden() {
        client.mutateWith(mockOidcLogin().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
            .get().uri("/api/product/backoffice/brands").exchange()
            .expectStatus().isForbidden();
    }

    @Test
    void admin_authentication_returnsUsername() {
        client.mutateWith(mockOidcLogin()
                .idToken(token -> token.claim("preferred_username", "admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .get().uri("/authentication").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.isAuthenticated").isEqualTo(true)
            .jsonPath("$.authenticatedUser.username").isEqualTo("admin");
    }

    @Test
    void logout_withoutSession_redirectsHome() {
        client.post().uri("/logout").exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().location("/");
    }
}
