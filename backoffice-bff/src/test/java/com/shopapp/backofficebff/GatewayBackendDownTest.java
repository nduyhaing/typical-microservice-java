package com.shopapp.backofficebff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOAuth2Login;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * When the API behind the gateway is down, the back office must get an error fast, not hang.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient(timeout = "10s")
@Import(RedisContainerSupport.class)
class GatewayBackendDownTest {

    @Autowired
    private WebTestClient client;

    @DynamicPropertySource
    static void closedBackend(DynamicPropertyRegistry registry) {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        registry.add("API_BASE_URL", () -> "http://127.0.0.1:" + closedPort);
        registry.add("BACKOFFICE_URL", () -> "http://127.0.0.1:" + closedPort);
    }

    @Test
    void apiRoute_backendDown_returns5xxQuickly() {
        Instant start = Instant.now();

        client.mutateWith(mockOAuth2Login().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
            .get().uri("/api/product/backoffice/brands").exchange()
            .expectStatus().is5xxServerError();

        assertThat(Duration.between(start, Instant.now())).isLessThan(Duration.ofSeconds(5));
    }
}
