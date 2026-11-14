package com.shopapp.storefrontbff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Login;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The BFF holds the user's tokens in its session and relays the access token to the API; the browser never sees it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(RedisContainerSupport.class)
class GatewayTokenRelayTest {

    private static final AtomicReference<String> AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer API = api();

    @Autowired
    private WebTestClient client;

    @Autowired
    private ReactiveClientRegistrationRepository registrations;

    private static HttpServer api() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("API_BASE_URL", () -> "http://127.0.0.1:" + API.getAddress().getPort());
    }

    @AfterAll
    static void stopApi() {
        API.stop(0);
    }

    @BeforeEach
    void reset() {
        AUTHORIZATION.set("none");
    }

    @Test
    void apiRoute_loggedIn_relaysBearerToken() {
        ClientRegistration keycloak = registrations.findByRegistrationId("keycloak").block();

        // the login mutator stores an authorized client whose access token is "access-token" in the session
        client.mutateWith(mockOAuth2Login().clientRegistration(keycloak))
            .get().uri("/api/cart/storefront/cart/items").exchange()
            .expectStatus().isOk();

        assertThat(AUTHORIZATION.get()).isEqualTo("Bearer access-token");
    }

    @Test
    void apiRoute_anonymous_sendsNoAuthorization() {
        client.get().uri("/api/product/storefront/categories").exchange().expectStatus().isOk();

        assertThat(AUTHORIZATION.get()).isNull();
    }
}
