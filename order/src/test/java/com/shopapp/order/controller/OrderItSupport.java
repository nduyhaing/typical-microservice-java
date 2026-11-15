package com.shopapp.order.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.order.PostgresContainerSupport;
import com.shopapp.order.service.ServiceTokenProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Shared Spring context and JDK HttpServer stubs of the services order calls: product (checkout prices with tax
 * class, stock subtract/restore), cart (cleanup), tax (location-based batch), location (saved addresses,
 * countries, states, districts) and promotion (coupon verify, apply-usage, release-usage). One base class so every order IT shares one context and container.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
abstract class OrderItSupport {

    /** A request recorded by a stub: query parameters, body and Authorization header. */
    record Call(Map<String, List<String>> query, String body, String authorization) {

        String param(String name) {
            List<String> values = query.get(name);
            return values == null || values.isEmpty() ? null : values.getFirst();
        }
    }

    static final AtomicBoolean PRODUCT_DOWN = new AtomicBoolean(false);
    static final AtomicBoolean CART_DOWN = new AtomicBoolean(false);
    static final AtomicBoolean TAX_DOWN = new AtomicBoolean(false);
    static final AtomicBoolean LOCATION_DOWN = new AtomicBoolean(false);
    /** Status of the stock subtract stub: 204 taken, 400 not enough stock, 503 down. */
    static final AtomicInteger SUBTRACT_STATUS = new AtomicInteger(204);
    static final AtomicReference<String> CART_AUTHORIZATION = new AtomicReference<>();
    static final AtomicReference<String> CART_BODY = new AtomicReference<>();
    static final List<Call> SUBTRACT_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> RESTORE_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> TAX_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> ADDRESS_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> VARIATION_CALLS = new CopyOnWriteArrayList<>();
    static final AtomicBoolean PROMOTION_DOWN = new AtomicBoolean(false);
    /** Status and body of the promotion verify stub (default: unknown code). */
    static final AtomicReference<Answer> VERIFY_ANSWER = new AtomicReference<>();
    /** Status and body of the promotion apply-usage stub (default: 200). */
    static final AtomicReference<Answer> APPLY_ANSWER = new AtomicReference<>();
    static final List<Call> VERIFY_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> APPLY_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> RELEASE_CALLS = new CopyOnWriteArrayList<>();
    /** Internal (service token) stock restore and coupon release that give a cancelled order's goods back (5-4). */
    static final List<Call> INTERNAL_RESTORE_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> INTERNAL_RELEASE_CALLS = new CopyOnWriteArrayList<>();
    static final AtomicInteger INTERNAL_RESTORE_STATUS = new AtomicInteger(204);
    static final AtomicInteger INTERNAL_RELEASE_STATUS = new AtomicInteger(204);
    /** Client-credentials token requests to the stubbed Keycloak, and the status it answers. */
    static final List<Call> TOKEN_CALLS = new CopyOnWriteArrayList<>();
    static final AtomicInteger TOKEN_STATUS = new AtomicInteger(200);
    static final String SERVICE_TOKEN = "service-token-order";
    static final HttpServer BACKENDS = backends();

    /** A stubbed answer. */
    record Answer(int status, String body) {
    }

    static Answer promotionError(String code, String detail) {
        return new Answer(400, "{\"statusCode\":\"400 BAD_REQUEST\",\"title\":\"" + code + "\",\"detail\":\"" + detail
            + "\",\"fieldErrors\":[]}");
    }

    /** A valid coupon as promotion's verify answers it (decisions.md 4-43). */
    static Answer validCoupon(String code, String discountType, String discountValue, String eligibleIds) {
        return new Answer(200, "{\"isValid\":true,\"productId\":" + eligibleIds.split(",")[0] + ",\"productIds\":["
            + eligibleIds + "],\"couponCode\":\"" + code + "\",\"discountType\":\"" + discountType
            + "\",\"discountValue\":" + discountValue + ",\"promotionId\":9,\"promotionName\":\"Promo " + code + "\"}");
    }

    /** Orderable products: id -> JSON (25 and 28 in tax class 1, 26 in class 2 without a rate, 27 without class). */
    private static final Map<Long, String> PRODUCTS = Map.of(
        25L, "{\"id\":25,\"name\":\"Galaxy S24 Violet\",\"price\":819.0,\"taxClassId\":1}",
        26L, "{\"id\":26,\"name\":\"USB-C Cable\",\"price\":19.0,\"taxClassId\":2}",
        27L, "{\"id\":27,\"name\":\"Phone Case\",\"price\":10.0,\"taxClassId\":null}",
        28L, "{\"id\":28,\"name\":\"Sticker\",\"price\":0.25,\"taxClassId\":1}");

    /** A 300-character address line: location allows 450 (3c review I2). */
    static final String LONG_LINE = "9".repeat(300);

    /**
     * Saved addresses in location: id -> token location answers it to ({@code *}: anyone, as location does for an
     * admin), JSON with its owner in {@code createdBy}. 100 belongs to u-1, 200 to u-2; 300 belongs to u-2 but is
     * answered to anyone; 400 (u-1) has a 300-character line; 500 (u-1) a line longer than any order column.
     */
    private static final Map<String, String[]> ADDRESSES = Map.of(
        "100", new String[] {"Bearer token-u-1", "{\"id\":100,\"contactName\":\"Customer One\",\"phone\":\"0901\","
            + "\"addressLine1\":\"1 Le Loi\",\"addressLine2\":null,\"city\":\"Ho Chi Minh City\",\"zipCode\":\"700000\","
            + "\"districtId\":100,\"districtName\":\"District 1\",\"stateOrProvinceId\":10,"
            + "\"stateOrProvinceName\":\"Ho Chi Minh\",\"countryId\":1,\"countryName\":\"Viet Nam\",\"createdBy\":\"u-1\"}"},
        "200", new String[] {"Bearer token-u-2", savedAddress(200, "2 Main St", "u-2")},
        "300", new String[] {"*", savedAddress(300, "3 Main St", "u-2")},
        "400", new String[] {"Bearer token-u-1", savedAddress(400, LONG_LINE, "u-1")},
        "500", new String[] {"Bearer token-u-1", savedAddress(500, "5".repeat(600), "u-1")});

    private static String savedAddress(long id, String line1, String owner) {
        return "{\"id\":" + id + ",\"contactName\":\"Customer\",\"phone\":\"0902\",\"addressLine1\":\"" + line1
            + "\",\"addressLine2\":null,\"city\":\"Hanoi\",\"zipCode\":null,\"districtId\":null,"
            + "\"districtName\":null,\"stateOrProvinceId\":null,\"stateOrProvinceName\":null,\"countryId\":1,"
            + "\"countryName\":\"Viet Nam\",\"createdBy\":\"" + owner + "\"}";
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ServiceTokenProvider serviceTokens;

    private static HttpServer backends() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/product/products", exchange -> {
                List<String> ids = query(exchange).getOrDefault("ids", List.of());
                String items = ids.stream().map(Long::valueOf).filter(PRODUCTS::containsKey).distinct()
                    .map(PRODUCTS::get).collect(Collectors.joining(","));
                respond(exchange, PRODUCT_DOWN.get() ? 503 : 200,
                    "{\"productCheckoutListVms\":[" + items + "],\"pageNo\":0,\"pageSize\":100,\"totalElements\":0,"
                        + "\"totalPages\":1,\"isLast\":true}");
            });
            // product 40 is a parent whose variations are 25 and 28; 404 is unknown; any other id has none
            server.createContext("/product/storefront/product-variations/", exchange -> {
                VARIATION_CALLS.add(record(exchange));
                String id = exchange.getRequestURI().getPath()
                    .substring("/product/storefront/product-variations/".length());
                if (PRODUCT_DOWN.get()) {
                    respond(exchange, 503, "");
                } else if (id.equals("404")) {
                    respond(exchange, 404, "{\"statusCode\":\"404 NOT_FOUND\",\"title\":\"Not Found\","
                        + "\"detail\":\"Product 404 is not found\",\"fieldErrors\":[]}");
                } else {
                    respond(exchange, 200, id.equals("40")
                        ? "[{\"id\":25,\"name\":\"Violet\"},{\"id\":28,\"name\":\"Sticker\"}]" : "[]");
                }
            });
            server.createContext("/product/backoffice/products/subtract-quantity", exchange -> {
                SUBTRACT_CALLS.add(record(exchange));
                int status = SUBTRACT_STATUS.get();
                respond(exchange, status, status == 400
                    ? "{\"statusCode\":\"400 BAD_REQUEST\",\"title\":\"NOT_ENOUGH_STOCK\","
                        + "\"detail\":\"Not enough stock for product 25\",\"fieldErrors\":[\"productId 25\"]}"
                    : "");
            });
            server.createContext("/product/backoffice/products/restore-quantity", exchange -> {
                RESTORE_CALLS.add(record(exchange));
                respond(exchange, 204, "");
            });
            server.createContext("/product/internal/products/restore-quantity", exchange -> {
                INTERNAL_RESTORE_CALLS.add(record(exchange));
                respond(exchange, INTERNAL_RESTORE_STATUS.get(), "");
            });
            server.createContext("/promotion/internal/promotions/release-usage", exchange -> {
                INTERNAL_RELEASE_CALLS.add(record(exchange));
                respond(exchange, INTERNAL_RELEASE_STATUS.get(), "");
            });
            server.createContext("/identity/token", exchange -> {
                TOKEN_CALLS.add(record(exchange));
                int status = TOKEN_STATUS.get();
                respond(exchange, status, status == 200
                    ? "{\"access_token\":\"" + SERVICE_TOKEN + "\",\"expires_in\":300,\"token_type\":\"Bearer\"}"
                    : "{\"error\":\"unauthorized_client\"}");
            });
            server.createContext("/cart/storefront/cart/items/remove", exchange -> {
                Call call = record(exchange);
                CART_AUTHORIZATION.set(call.authorization());
                CART_BODY.set(call.body());
                respond(exchange, CART_DOWN.get() ? 503 : 200, "[]");
            });
            server.createContext("/tax/backoffice/tax-rates/location-based-batch", exchange -> {
                Call call = record(exchange);
                TAX_CALLS.add(call);
                // country 1 (Viet Nam): class 1 has a 10 % rate; class 2 has no rate and is left out (3c-11)
                boolean classOne = call.query().getOrDefault("taxClassIds", List.of()).contains("1");
                String body = "1".equals(call.param("countryId")) && classOne
                    ? "[{\"id\":5,\"rate\":10.0,\"zipCode\":null,\"taxClassId\":1,\"stateOrProvinceId\":null,"
                        + "\"countryId\":1}]"
                    : "[]";
                respond(exchange, TAX_DOWN.get() ? 503 : 200, body);
            });
            server.createContext("/location/storefront/addresses/", exchange -> {
                Call call = record(exchange);
                ADDRESS_CALLS.add(call);
                String id = exchange.getRequestURI().getPath().substring("/location/storefront/addresses/".length());
                String[] address = ADDRESSES.get(id);
                if (LOCATION_DOWN.get()) {
                    respond(exchange, 503, "");
                } else if (address == null || !(address[0].equals("*") || address[0].equals(call.authorization()))) {
                    // location answers 404 for another user's address (3c-5)
                    respond(exchange, 404, "{\"statusCode\":\"404 NOT_FOUND\",\"title\":\"Not Found\","
                        + "\"detail\":\"Address " + id + " is not found\",\"fieldErrors\":[]}");
                } else {
                    respond(exchange, 200, address[1]);
                }
            });
            server.createContext("/location/storefront/countries", exchange -> respond(exchange,
                LOCATION_DOWN.get() ? 503 : 200,
                "[{\"id\":1,\"code2\":\"VN\",\"name\":\"Viet Nam\"},{\"id\":2,\"code2\":\"US\",\"name\":\"United States\"}]"));
            server.createContext("/location/storefront/state-or-provinces/", exchange -> {
                boolean vietNam = exchange.getRequestURI().getPath().endsWith("/1");
                respond(exchange, LOCATION_DOWN.get() ? 503 : 200, vietNam
                    ? "[{\"id\":10,\"name\":\"Ho Chi Minh\",\"code\":null,\"type\":null,\"countryId\":1}]" : "[]");
            });
            server.createContext("/location/storefront/district/", exchange -> {
                boolean hoChiMinh = exchange.getRequestURI().getPath().endsWith("/10");
                respond(exchange, LOCATION_DOWN.get() ? 503 : 200,
                    hoChiMinh ? "[{\"id\":100,\"name\":\"District 1\"}]" : "[]");
            });
            server.createContext("/promotion/storefront/promotions/verify", exchange -> {
                VERIFY_CALLS.add(record(exchange));
                Answer answer = VERIFY_ANSWER.get();
                respond(exchange, PROMOTION_DOWN.get() ? 503 : answer.status(), answer.body());
            });
            server.createContext("/promotion/storefront/promotions/apply-usage", exchange -> {
                Call call = record(exchange);
                APPLY_CALLS.add(call);
                Answer answer = APPLY_ANSWER.get();
                respond(exchange, PROMOTION_DOWN.get() ? 503 : answer.status(), answer.status() == 200
                    ? "{\"promotionId\":9,\"couponCode\":\"X\",\"reference\":\"" + call.param("reference") + "\"}"
                    : answer.body());
            });
            server.createContext("/promotion/storefront/promotions/release-usage", exchange -> {
                RELEASE_CALLS.add(record(exchange));
                respond(exchange, 204, "");
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Map<String, List<String>> query(HttpExchange exchange) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        return Arrays.stream(raw.split("&"))
            .map(pair -> pair.split("=", 2))
            .flatMap(pair -> Arrays.stream(URLDecoder.decode(pair.length > 1 ? pair[1] : "", StandardCharsets.UTF_8)
                .split(",")).map(value -> Map.entry(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), value)))
            .collect(Collectors.groupingBy(Map.Entry::getKey,
                Collectors.mapping(Map.Entry::getValue, Collectors.toCollection(ArrayList::new))));
    }

    private static Call record(HttpExchange exchange) throws IOException {
        return new Call(query(exchange), new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
            exchange.getRequestHeaders().getFirst("Authorization"));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + BACKENDS.getAddress().getPort();
        registry.add("shopapp.services.product", () -> base + "/product");
        registry.add("shopapp.services.cart", () -> base + "/cart");
        registry.add("shopapp.services.tax", () -> base + "/tax");
        registry.add("shopapp.services.location", () -> base + "/location");
        registry.add("shopapp.services.promotion", () -> base + "/promotion");
        registry.add("shopapp.service-auth.token-uri", () -> base + "/identity/token");
        registry.add("shopapp.service-auth.client-id", () -> "order-service");
        registry.add("shopapp.service-auth.client-secret", () -> "order-secret");
        // the retry sweep stays out of the way; tests call it directly
        registry.add("shopapp.order.goods-back-retry", () -> "PT10H");
    }

    // BACKENDS is shared by every IT class of this JVM (one Spring context); the JVM exit stops it

    @BeforeEach
    void resetBackends() {
        PRODUCT_DOWN.set(false);
        CART_DOWN.set(false);
        TAX_DOWN.set(false);
        LOCATION_DOWN.set(false);
        SUBTRACT_STATUS.set(204);
        CART_AUTHORIZATION.set(null);
        CART_BODY.set(null);
        SUBTRACT_CALLS.clear();
        RESTORE_CALLS.clear();
        TAX_CALLS.clear();
        ADDRESS_CALLS.clear();
        VARIATION_CALLS.clear();
        PROMOTION_DOWN.set(false);
        VERIFY_ANSWER.set(promotionError("PROMOTION_NOT_FOUND", "Promotion is not found"));
        APPLY_ANSWER.set(new Answer(200, ""));
        VERIFY_CALLS.clear();
        APPLY_CALLS.clear();
        RELEASE_CALLS.clear();
        INTERNAL_RESTORE_CALLS.clear();
        INTERNAL_RELEASE_CALLS.clear();
        INTERNAL_RESTORE_STATUS.set(204);
        INTERNAL_RELEASE_STATUS.set(204);
        TOKEN_CALLS.clear();
        TOKEN_STATUS.set(200);
        serviceTokens.evict();
        jdbc.execute("TRUNCATE order_item, \"order\", order_address, checkout_item, checkout RESTART IDENTITY CASCADE");
    }

    static RequestPostProcessor customer(String id) {
        return jwt().jwt(token -> token.subject(id).claim("email", id + "@shopapp.local").tokenValue("token-" + id));
    }

    static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-1").tokenValue("token-admin"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    /** The payment service's client-credentials token (realm role SERVICE). */
    static RequestPostProcessor service() {
        return jwt().jwt(token -> token.subject("service-account-payment").tokenValue("token-service"))
            .authorities(new SimpleGrantedAuthority("ROLE_SERVICE"));
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    /** A typed address in the United States (country 2), where the stubbed tax service has no rates. */
    static final String ADDRESS = """
        {"contactName":"Customer Dev","phone":"0123456789","addressLine1":"1 Main St","city":"Springfield",
         "zipCode":"62701","countryId":2}""";

    String createCheckout(String customer, String items) throws Exception {
        String response = mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":" + items + "}").with(customer(customer)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    /** Place order with a typed address. */
    MockHttpServletRequestBuilder placeOrder(String checkoutId, String extra) {
        return json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressPostVm\":"
            + ADDRESS + ",\"paymentMethod\":\"COD\"" + extra + "}");
    }

    /** Place order with a saved address. */
    MockHttpServletRequestBuilder placeOrderWithSavedAddress(String checkoutId, long addressId) {
        return json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressId\":"
            + addressId + ",\"paymentMethod\":\"COD\"}");
    }

    /** Places an order for the customer and answers its id. */
    long placedOrder(String customer, String items, String extra) throws Exception {
        String response = mockMvc.perform(placeOrder(createCheckout(customer, items), extra).with(customer(customer)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    long orderCount() {
        Long count = jdbc.queryForObject("select count(*) from \"order\"", Long.class);
        return count == null ? 0 : count;
    }
}
