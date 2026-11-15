package com.shopapp.backofficebff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Login;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * An admin's requests: {@code /api/**} goes to the API gateway without the prefix and with the admin's access token
 * (the browser never sees it); everything else goes to the Next.js back office.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(RedisContainerSupport.class)
class GatewayRoutesTest {

    private static final AtomicReference<String> API_PATH = new AtomicReference<>();
    private static final AtomicReference<String> API_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> BACKOFFICE_PATH = new AtomicReference<>();
    private static final HttpServer API = backend(API_PATH, API_AUTHORIZATION, "{\"name\":\"Apple\"}");
    private static final HttpServer BACKOFFICE = backend(BACKOFFICE_PATH, new AtomicReference<>(), "<html>admin</html>");

    /** Product's order-only stock endpoints: reachable inside the compose network only (3c review I3). */
    private static final List<String> STOCK_WRITES = List.of(
        "/api/product/backoffice/products/subtract-quantity?reference=r-1",
        "/api/product/backoffice/products/restore-quantity?reference=r-1",
        "/api/product/backoffice/products/subtract%2Dquantity?reference=r-1",
        "/api/product/backoffice/products/restore-quantity/");

    @Autowired
    private WebTestClient client;

    @Autowired
    private ReactiveClientRegistrationRepository registrations;

    private static HttpServer backend(AtomicReference<String> seenPath, AtomicReference<String> seenAuthorization,
                                      String body) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                seenPath.set(exchange.getRequestURI().getPath());
                seenAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
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
        registry.add("BACKOFFICE_URL", () -> "http://127.0.0.1:" + BACKOFFICE.getAddress().getPort());
    }

    @AfterAll
    static void stopBackends() {
        API.stop(0);
        BACKOFFICE.stop(0);
    }

    @BeforeEach
    void reset() {
        API_PATH.set(null);
        API_AUTHORIZATION.set(null);
        BACKOFFICE_PATH.set(null);
    }

    private WebTestClient admin() {
        ClientRegistration keycloak = registrations.findByRegistrationId("keycloak").block();
        // the login mutator stores an authorized client whose access token is "access-token" in the session
        return client.mutateWith(mockOAuth2Login().clientRegistration(keycloak)
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Test
    void apiRoute_stripsPrefixAndRelaysAdminToken() {
        admin().get().uri("/api/product/backoffice/brands").exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("{\"name\":\"Apple\"}");

        assertThat(API_PATH.get()).isEqualTo("/product/backoffice/brands");
        assertThat(API_AUTHORIZATION.get()).isEqualTo("Bearer access-token");
        assertThat(BACKOFFICE_PATH.get()).isNull();
    }

    @Test
    void otherPaths_goToBackofficeApp() {
        admin().get().uri("/catalog/products").exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("<html>admin</html>");

        assertThat(BACKOFFICE_PATH.get()).isEqualTo("/catalog/products");
        assertThat(API_PATH.get()).isNull();
    }

    @Test
    void stockWriteEndpoints_areRefused_evenForAnAdmin() {
        for (String path : STOCK_WRITES) {
            admin().put().uri(URI.create(path)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[{\"productId\":1,\"quantity\":1}]").exchange()
                .expectStatus().isForbidden();
        }
        assertThat(API_PATH.get()).isNull();
    }

    @Test
    void anonymous_neverReachesBackends() {
        client.get().uri("/api/product/backoffice/brands").exchange()
            .expectStatus().isUnauthorized();
        client.get().uri("/catalog/products").exchange()
            .expectStatus().is3xxRedirection();

        assertThat(API_PATH.get()).isNull();
        assertThat(BACKOFFICE_PATH.get()).isNull();
    }
}
