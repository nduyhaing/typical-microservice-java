package com.shopapp.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.order.PostgresContainerSupport;
import com.shopapp.order.model.enumeration.CheckoutState;
import com.shopapp.order.repository.CheckoutRepository;
import com.shopapp.order.repository.OrderRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
class CheckoutOrderIT {

    private static final AtomicBoolean PRODUCT_DOWN = new AtomicBoolean(false);
    private static final AtomicBoolean CART_DOWN = new AtomicBoolean(false);
    private static final AtomicReference<String> CART_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> CART_BODY = new AtomicReference<>();
    private static final HttpServer BACKENDS = backends();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CheckoutRepository checkoutRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JdbcTemplate jdbc;

    /**
     * Product (/product/products, orderable products 25 = 819.0 and 26 = 19.0) and cart (/cart/.../remove) stubs.
     */
    private static HttpServer backends() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/product/products", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                StringBuilder items = new StringBuilder();
                if (query.contains("25")) {
                    items.append("{\"id\":25,\"name\":\"Galaxy S24 Violet\",\"price\":819.0}");
                }
                if (query.contains("26")) {
                    items.append(items.isEmpty() ? "" : ",").append("{\"id\":26,\"name\":\"USB-C Cable\",\"price\":19.0}");
                }
                respond(exchange, PRODUCT_DOWN.get() ? 503 : 200,
                    "{\"productCheckoutListVms\":[" + items + "],\"pageNo\":0,\"pageSize\":100,\"totalElements\":0,"
                        + "\"totalPages\":1,\"isLast\":true}");
            });
            server.createContext("/cart/storefront/cart/items/remove", exchange -> {
                CART_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
                CART_BODY.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                respond(exchange, CART_DOWN.get() ? 503 : 200, "[]");
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + BACKENDS.getAddress().getPort();
        registry.add("shopapp.services.product", () -> base + "/product");
        registry.add("shopapp.services.cart", () -> base + "/cart");
    }

    @AfterAll
    static void stopBackends() {
        BACKENDS.stop(0);
    }

    @BeforeEach
    void reset() {
        PRODUCT_DOWN.set(false);
        CART_DOWN.set(false);
        CART_AUTHORIZATION.set(null);
        CART_BODY.set(null);
        jdbc.execute("TRUNCATE order_item, \"order\", order_address, checkout_item, checkout RESTART IDENTITY CASCADE");
    }

    private static RequestPostProcessor customer(String id) {
        return jwt().jwt(token -> token.subject(id).claim("email", id + "@shopapp.local").tokenValue("token-" + id));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    private static final String ADDRESS = """
        {"contactName":"Customer Dev","phone":"0123456789","addressLine1":"1 Main St","city":"Ho Chi Minh City",
         "zipCode":"700000","countryName":"Vietnam"}""";

    private String createCheckout(String customer, String items) throws Exception {
        String response = mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":" + items + "}").with(customer(customer)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private MockHttpServletRequestBuilder placeOrder(String checkoutId, String extra) {
        return json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressPostVm\":"
            + ADDRESS + ",\"paymentMethod\":\"COD\"" + extra + "}");
    }

    @Test
    void createCheckout_pricesFromProductService() throws Exception {
        mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":[{\"productId\":25,\"quantity\":2},{\"productId\":26,\"quantity\":1}]}")
                .with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(1657.0))
            .andExpect(jsonPath("$.checkoutState").value("PENDING"))
            .andExpect(jsonPath("$.checkoutItemVms[*].productName")
                .value(containsInAnyOrder("Galaxy S24 Violet", "USB-C Cable")));
    }

    @Test
    void createCheckout_notOrderable_returns404() throws Exception {
        mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":[{\"productId\":1,\"quantity\":1}]}").with(customer("u-1")))
            .andExpect(status().isNotFound());

        assertThat(checkoutRepository.findAll()).isEmpty();
    }

    @Test
    void createCheckout_productDown_returns5xx() throws Exception {
        PRODUCT_DOWN.set(true);

        mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":[{\"productId\":25,\"quantity\":1}]}").with(customer("u-1")))
            .andExpect(status().is5xxServerError());

        assertThat(checkoutRepository.findAll()).isEmpty();
    }

    @Test
    void createCheckout_quantityOutOfRange_returns400() throws Exception {
        mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":[{\"productId\":25,\"quantity\":1001}]}").with(customer("u-1")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/storefront/checkouts"), "{\"checkoutItemPostVms\":[]}").with(customer("u-1")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getCheckout_foreign_returns403() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(get("/storefront/checkouts/{id}", checkoutId).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(819.0));
        mockMvc.perform(get("/storefront/checkouts/{id}", checkoutId).with(customer("u-2")))
            .andExpect(status().isForbidden());
    }

    @Test
    void placeOrder_createsPendingPaymentOrderFromCheckout() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":2}]");

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
            .andExpect(jsonPath("$.totalPrice").value(1638.0))
            .andExpect(jsonPath("$.email").value("u-1@shopapp.local"))
            .andExpect(jsonPath("$.orderItemVms[0].productName").value("Galaxy S24 Violet"))
            .andExpect(jsonPath("$.shippingAddressVm.city").value("Ho Chi Minh City"));

        assertThat(checkoutRepository.findById(checkoutId)).get()
            .extracting(c -> c.getCheckoutState()).isEqualTo(CheckoutState.COMPLETED);
    }

    @Test
    void placeOrder_ignoresClientTotal() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrder(checkoutId, ",\"totalPrice\":1,\"tax\":0,\"discount\":818").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPrice").value(819.0));
    }

    @Test
    void placeOrder_removesItemsFromCartWithCallersToken() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":2}]");

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isOk());

        assertThat(CART_AUTHORIZATION.get()).isEqualTo("Bearer token-u-1");
        assertThat(CART_BODY.get()).contains("\"productId\":25").contains("\"quantity\":2");
    }

    @Test
    void placeOrder_cartDown_stillCreatesOrder() throws Exception {
        CART_DOWN.set(true);
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isOk());

        assertThat(orderRepository.findByCheckoutId(checkoutId)).isPresent();
    }

    @Test
    void placeOrder_foreignCheckout_returns403() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-2"))).andExpect(status().isForbidden());

        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void placeOrder_twice_returns400() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isOk());

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isBadRequest());

        assertThat(orderRepository.findAll()).hasSize(1);
    }

    @Test
    void placeOrder_missingAddress_returns400() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId
                + "\",\"shippingAddressPostVm\":{\"contactName\":\"\",\"phone\":\"1\"},\"paymentMethod\":\"COD\"}")
                .with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void myOrders_returnsOnlyOwnNewestFirst() throws Exception {
        String first = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        mockMvc.perform(placeOrder(first, "").with(customer("u-1"))).andExpect(status().isOk());
        String second = createCheckout("u-1", "[{\"productId\":26,\"quantity\":3}]");
        mockMvc.perform(placeOrder(second, "").with(customer("u-1"))).andExpect(status().isOk());
        String other = createCheckout("u-2", "[{\"productId\":26,\"quantity\":1}]");
        mockMvc.perform(placeOrder(other, "").with(customer("u-2"))).andExpect(status().isOk());

        mockMvc.perform(get("/storefront/orders/my-orders").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].checkoutId").value(second))
            .andExpect(jsonPath("$[0].totalPrice").value(57.0))
            .andExpect(jsonPath("$[1].checkoutId").value(first));
    }

    @Test
    void orderByCheckout_returnsOwnOrder() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isOk());

        mockMvc.perform(get("/storefront/orders/checkout/{id}", checkoutId).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkoutId").value(checkoutId));
        mockMvc.perform(get("/storefront/orders/checkout/{id}", checkoutId).with(customer("u-2")))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_returns4xx() throws Exception {
        mockMvc.perform(get("/storefront/orders/my-orders")).andExpect(status().is4xxClientError());
        mockMvc.perform(json(post("/storefront/checkouts"), "{\"checkoutItemPostVms\":[{\"productId\":25,\"quantity\":1}]}"))
            .andExpect(status().is4xxClientError());
    }
}
