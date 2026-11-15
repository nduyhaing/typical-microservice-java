package com.shopapp.cart.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.cart.PostgresContainerSupport;
import com.shopapp.cart.model.CartItem;
import com.shopapp.cart.model.CartItemId;
import com.shopapp.cart.repository.CartItemRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
class CartItemControllerIT {

    private static final Set<Long> PUBLISHED_PRODUCTS = Set.of(10L, 11L, 12L, 13L);
    /** Stock-tracked products: 12 has 3 units, 13 is out of stock; 10 and 11 are not tracked. */
    private static final Map<Long, Integer> TRACKED_STOCK = Map.of(12L, 3, 13L, 0);
    private static final AtomicBoolean PRODUCT_SERVICE_DOWN = new AtomicBoolean(false);
    private static final HttpServer PRODUCT_SERVICE = productService();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CartItemRepository repository;

    /** Stub of the product service's list-featured endpoint. */
    private static HttpServer productService() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/product/storefront/products/list-featured", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                long id = Long.parseLong(query.substring(query.indexOf('=') + 1));
                int status = PRODUCT_SERVICE_DOWN.get() ? 503 : 200;
                String body = PRODUCT_SERVICE_DOWN.get() ? "" : PUBLISHED_PRODUCTS.contains(id)
                    ? "[{\"id\":" + id + ",\"name\":\"P" + id + "\",\"slug\":\"p\",\"thumbnailUrl\":\"\",\"price\":1.0,"
                        + stock(id) + "}]"
                    : "[]";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    exchange.getResponseBody().write(bytes);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static String stock(long id) {
        Integer quantity = TRACKED_STOCK.get(id);
        return quantity == null
            ? "\"stockTrackingEnabled\":false,\"stockAvailable\":true,\"stockQuantity\":null"
            : "\"stockTrackingEnabled\":true,\"stockAvailable\":" + (quantity > 0) + ",\"stockQuantity\":" + quantity;
    }

    @DynamicPropertySource
    static void productServiceUrl(DynamicPropertyRegistry registry) {
        registry.add("shopapp.services.product",
            () -> "http://127.0.0.1:" + PRODUCT_SERVICE.getAddress().getPort() + "/product");
    }

    @AfterAll
    static void stopProductService() {
        PRODUCT_SERVICE.stop(0);
    }

    @BeforeEach
    void emptyCarts() {
        PRODUCT_SERVICE_DOWN.set(false);
        repository.deleteAll();
    }

    private static RequestPostProcessor user(String id) {
        return jwt().jwt(token -> token.subject(id));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private void stored(String customer, long product, int quantity) {
        repository.save(CartItem.builder().customerId(customer).productId(product).quantity(quantity).build());
    }

    @Test
    void add_createsItem() throws Exception {
        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":10,\"quantity\":2}").with(user("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.customerId").value("u-1"))
            .andExpect(jsonPath("$.productId").value(10))
            .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void add_existing_mergesQuantity() throws Exception {
        stored("u-1", 10L, 2);

        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":10,\"quantity\":3}").with(user("u-1")))
            .andExpect(jsonPath("$.quantity").value(5));
    }

    @Test
    void add_zeroQuantity_returns400() throws Exception {
        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":10,\"quantity\":0}").with(user("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400 BAD_REQUEST"));
    }

    @Test
    void add_unknownProduct_returns404() throws Exception {
        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":999,\"quantity\":1}").with(user("u-1")))
            .andExpect(status().isNotFound());

        org.assertj.core.api.Assertions.assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void add_productServiceDown_returns5xxWithoutSaving() throws Exception {
        PRODUCT_SERVICE_DOWN.set(true);

        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":10,\"quantity\":1}").with(user("u-1")))
            .andExpect(status().is5xxServerError());

        org.assertj.core.api.Assertions.assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void list_returnsOnlyCurrentUsersItems() throws Exception {
        stored("u-1", 10L, 1);
        stored("u-1", 11L, 2);
        stored("u-2", 10L, 9);

        mockMvc.perform(get("/storefront/cart/items").with(user("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].productId").value(containsInAnyOrder(10, 11)));
    }

    @Test
    void update_setsQuantity() throws Exception {
        stored("u-1", 10L, 1);

        mockMvc.perform(json(put("/storefront/cart/items/10"), "{\"quantity\":7}").with(user("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(7));
    }

    @Test
    void update_negativeQuantity_returns400() throws Exception {
        stored("u-1", 10L, 1);

        mockMvc.perform(json(put("/storefront/cart/items/10"), "{\"quantity\":-1}").with(user("u-1")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void delete_removesItem() throws Exception {
        stored("u-1", 10L, 1);
        stored("u-1", 11L, 1);

        mockMvc.perform(delete("/storefront/cart/items/10").with(user("u-1")))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/storefront/cart/items").with(user("u-1")))
            .andExpect(jsonPath("$[*].productId").value(containsInAnyOrder(11)));
    }

    @Test
    void removeMany_adjustsOrDeletes() throws Exception {
        stored("u-1", 10L, 5);
        stored("u-1", 11L, 1);

        mockMvc.perform(json(post("/storefront/cart/items/remove"),
                "[{\"productId\":10,\"quantity\":2},{\"productId\":11,\"quantity\":1}]").with(user("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].productId").value(10))
            .andExpect(jsonPath("$[0].quantity").value(3));

        mockMvc.perform(get("/storefront/cart/items").with(user("u-1")))
            .andExpect(jsonPath("$[*].productId").value(containsInAnyOrder(10)));
    }

    @Test
    void anonymous_returnsForbiddenErrorVm() throws Exception {
        mockMvc.perform(get("/storefront/cart/items"))
            .andExpect(status().is4xxClientError())
            .andExpect(jsonPath("$.statusCode").exists());
        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":10,\"quantity\":1}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void add_quantityAboveLimit_returns400() throws Exception {
        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":10,\"quantity\":1001}").with(user("u-1")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void update_hugeQuantity_returns400() throws Exception {
        stored("u-1", 10L, 1);

        mockMvc.perform(json(put("/storefront/cart/items/10"), "{\"quantity\":2147483647}").with(user("u-1")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void add_mergeBeyondLimit_returns400AndKeepsQuantity() throws Exception {
        stored("u-1", 10L, 1000);

        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":10,\"quantity\":1}").with(user("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.statusCode").value("400 BAD_REQUEST"));

        org.assertj.core.api.Assertions.assertThat(repository.findById(new CartItemId("u-1", 10L)))
            .get().extracting(CartItem::getQuantity).isEqualTo(1000);
    }

    @Test
    void add_trackedProductWithinStock_succeeds() throws Exception {
        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":12,\"quantity\":3}").with(user("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quantity").value(3));
    }

    @Test
    void add_trackedProductBeyondStock_returns400() throws Exception {
        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":12,\"quantity\":4}").with(user("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Only 3 of P12 are in stock"));

        org.assertj.core.api.Assertions.assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void add_mergeBeyondStock_returns400AndKeepsQuantity() throws Exception {
        stored("u-1", 12L, 2);

        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":12,\"quantity\":2}").with(user("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Only 3 of P12 are in stock"));

        org.assertj.core.api.Assertions.assertThat(repository.findById(new CartItemId("u-1", 12L)))
            .get().extracting(CartItem::getQuantity).isEqualTo(2);
    }

    @Test
    void update_trackedProductBeyondStock_returns400() throws Exception {
        stored("u-1", 12L, 1);

        mockMvc.perform(json(put("/storefront/cart/items/12"), "{\"quantity\":4}").with(user("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Only 3 of P12 are in stock"));
        mockMvc.perform(json(put("/storefront/cart/items/12"), "{\"quantity\":3}").with(user("u-1")))
            .andExpect(status().isOk());
    }

    @Test
    void add_outOfStockProduct_returns400() throws Exception {
        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":13,\"quantity\":1}").with(user("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("P13 is out of stock"));
    }

    @Test
    void add_untrackedProduct_ignoresStock() throws Exception {
        mockMvc.perform(json(post("/storefront/cart/items"), "{\"productId\":10,\"quantity\":500}").with(user("u-1")))
            .andExpect(status().isOk());
        mockMvc.perform(json(put("/storefront/cart/items/10"), "{\"quantity\":900}").with(user("u-1")))
            .andExpect(status().isOk());
    }
}
