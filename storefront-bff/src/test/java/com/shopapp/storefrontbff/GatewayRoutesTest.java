package com.shopapp.storefrontbff;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewayRoutesTest {

    private static final AtomicReference<String> API_PATH = new AtomicReference<>();
    private static final AtomicReference<String> STOREFRONT_PATH = new AtomicReference<>();
    private static final AtomicReference<Integer> API_STATUS = new AtomicReference<>(200);
    private static final HttpServer API = backend(API_PATH, API_STATUS, "{\"slug\":\"phones\"}");
    private static final HttpServer STOREFRONT = backend(STOREFRONT_PATH, new AtomicReference<>(200), "<html>shop</html>");

    @Autowired
    private WebTestClient client;

    private static HttpServer backend(AtomicReference<String> seenPath, AtomicReference<Integer> status, String body) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                seenPath.set(exchange.getRequestURI().getPath());
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status.get(), bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @DynamicPropertySource
    static void backends(DynamicPropertyRegistry registry) {
        registry.add("API_BASE_URL", () -> "http://127.0.0.1:" + API.getAddress().getPort());
        registry.add("STOREFRONT_URL", () -> "http://127.0.0.1:" + STOREFRONT.getAddress().getPort());
    }

    @AfterAll
    static void stopBackends() {
        API.stop(0);
        STOREFRONT.stop(0);
    }

    @BeforeEach
    void reset() {
        API_PATH.set(null);
        STOREFRONT_PATH.set(null);
        API_STATUS.set(200);
    }

    @Test
    void apiRoute_stripsPrefix() {
        client.get().uri("/api/product/storefront/categories").exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("{\"slug\":\"phones\"}");
        assertThat(API_PATH.get()).isEqualTo("/product/storefront/categories");
        assertThat(STOREFRONT_PATH.get()).isNull();
    }

    @Test
    void otherPaths_goToStorefront() {
        client.get().uri("/products/galaxy-s24").exchange()
            .expectStatus().isOk()
            .expectBody(String.class).isEqualTo("<html>shop</html>");
        assertThat(STOREFRONT_PATH.get()).isEqualTo("/products/galaxy-s24");
        assertThat(API_PATH.get()).isNull();
    }

    @Test
    void apiRoute_relays404() {
        API_STATUS.set(404);
        client.get().uri("/api/product/storefront/product/nope").exchange()
            .expectStatus().isNotFound()
            .expectBody(String.class).isEqualTo("{\"slug\":\"phones\"}");
    }
}
