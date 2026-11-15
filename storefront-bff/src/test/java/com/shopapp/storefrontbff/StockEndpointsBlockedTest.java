package com.shopapp.storefrontbff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockOidcLogin;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Product's order-only stock endpoints (subtract-quantity, restore-quantity) are reachable inside the compose network
 * only: the BFF refuses them, so a signed-in customer cannot take or give back stock (3c review I3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureWebTestClient
@Import(RedisContainerSupport.class)
class StockEndpointsBlockedTest {

    private static final List<String> STOCK_WRITES = List.of(
        "/api/product/backoffice/products/subtract-quantity?reference=r-1",
        "/api/product/backoffice/products/restore-quantity?reference=r-1",
        "/api/product/backoffice/products/subtract%2Dquantity?reference=r-1",
        "/api/product/backoffice/products/restore-quantity/");

    private static final AtomicReference<String> API_PATH = new AtomicReference<>();
    private static final HttpServer API = api();

    @Autowired
    private WebTestClient client;

    private static HttpServer api() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                API_PATH.set(exchange.getRequestURI().getPath());
                exchange.sendResponseHeaders(204, -1);
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

    @Test
    void signedInCustomer_isForbidden_anonymousIsRefused_andTheApiIsNeverCalled() {
        for (String path : STOCK_WRITES) {
            client.mutateWith(mockOidcLogin()).put().uri(URI.create(path)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[{\"productId\":1,\"quantity\":1}]").exchange()
                .expectStatus().isForbidden();
            client.put().uri(URI.create(path)).contentType(MediaType.APPLICATION_JSON)
                .bodyValue("[{\"productId\":1,\"quantity\":1}]").exchange()
                .expectStatus().value(status -> assertThat(status).isNotIn(200, 204));
        }
        assertThat(API_PATH.get()).isNull();
    }

    @Test
    void otherProductCalls_stillGoThrough() {
        client.mutateWith(mockOidcLogin()).get().uri("/api/product/storefront/products").exchange()
            .expectStatus().isNoContent();
        assertThat(API_PATH.get()).isEqualTo("/product/storefront/products");
    }
}
