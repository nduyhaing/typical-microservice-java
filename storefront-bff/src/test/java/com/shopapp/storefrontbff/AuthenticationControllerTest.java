package com.shopapp.storefrontbff;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(RedisContainerSupport.class)
class AuthenticationControllerTest {

    @Autowired
    private WebTestClient client;

    @Test
    void anonymous_isNotAuthenticated() {
        client.get().uri("/authentication").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.isAuthenticated").isEqualTo(false)
            .jsonPath("$.authenticatedUser").isEmpty();
    }

    @Test
    void loggedIn_returnsUsername() {
        client.mutateWith(mockOidcLogin().idToken(token -> token.claim("preferred_username", "customer")))
            .get().uri("/authentication").exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.isAuthenticated").isEqualTo(true)
            .jsonPath("$.authenticatedUser.username").isEqualTo("customer");
    }

    @Test
    void loginEntryPoint_redirectsToKeycloakAuthorizationEndpoint() {
        client.get().uri("/oauth2/authorization/keycloak").exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().value("Location", location -> org.assertj.core.api.Assertions.assertThat(location)
                .startsWith("http://identity.shopapp.local/realms/shopapp/protocol/openid-connect/auth")
                .contains("client_id=storefront-bff"));
    }

    @Test
    void logout_withoutSession_redirectsHome() {
        client.post().uri("/logout").exchange()
            .expectStatus().is3xxRedirection()
            .expectHeader().location("/");
    }
}
