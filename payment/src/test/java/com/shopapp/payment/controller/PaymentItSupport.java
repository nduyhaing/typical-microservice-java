package com.shopapp.payment.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.shopapp.payment.PostgresContainerSupport;
import com.shopapp.payment.service.PaypalClient;
import com.shopapp.payment.service.ServiceTokenProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
 * Shared Spring context and JDK HttpServer stubs of what payment calls: Keycloak's token endpoint (client
 * credentials), order's internal endpoints and payment-paypal's internal API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
abstract class PaymentItSupport {

    /** A request a stub received. */
    record Call(String method, String path, String body, String authorization) {
    }

    /** A stubbed answer. */
    record Answer(int status, String body) {
    }

    /** An order as order's internal GET answers it. */
    record StubOrder(long id, String customerId, String totalPrice, String orderStatus, String paymentStatus) {

        String json(Long paymentId) {
            return "{\"id\":" + id + ",\"checkoutId\":\"chk-" + id + "\",\"customerId\":\"" + customerId
                + "\",\"email\":\"" + customerId + "@shopapp.local\",\"totalPrice\":" + totalPrice
                + ",\"orderStatus\":\"" + orderStatus + "\",\"paymentStatus\":\"" + paymentStatus
                + "\",\"paymentMethod\":\"COD\",\"paymentId\":" + paymentId + "}";
        }
    }

    static final String SERVICE_TOKEN = "service-token-payment";
    static final Map<Long, StubOrder> ORDERS = new ConcurrentHashMap<>();
    static final Map<Long, Long> ORDER_PAYMENT_IDS = new ConcurrentHashMap<>();
    static final List<Call> ORDER_GET_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> ORDER_PAY_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> ORDER_IN_PROGRESS_CALLS = new CopyOnWriteArrayList<>();
    /** When set, the PayPal capture stub waits for it (a capture that is still running at PayPal). */
    static final AtomicReference<CountDownLatch> CAPTURE_GATE = new AtomicReference<>();
    /** When set, the PayPal create stub waits for it. */
    static final AtomicReference<CountDownLatch> CREATE_GATE = new AtomicReference<>();
    /** Status of order's mark-paid stub: 200 marks the stub order paid, anything else is answered as is. */
    static final AtomicInteger ORDER_PAY_STATUS = new AtomicInteger(200);
    static final AtomicBoolean ORDER_DOWN = new AtomicBoolean(false);
    static final List<Call> TOKEN_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> PAYPAL_CREATE_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> PAYPAL_CAPTURE_CALLS = new CopyOnWriteArrayList<>();
    static final AtomicBoolean PAYPAL_CONFIGURED = new AtomicBoolean(true);
    /** Answer of the capture stub; null = completed capture of the amount/currency/custom id below. */
    static final AtomicReference<Answer> PAYPAL_CAPTURE_ANSWER = new AtomicReference<>();
    static final AtomicReference<String> CAPTURED_AMOUNT = new AtomicReference<>();
    static final AtomicReference<String> CAPTURED_CURRENCY = new AtomicReference<>();
    /** The custom id PayPal echoes back; null = the one the create call sent. */
    static final AtomicReference<String> CAPTURED_CUSTOM_ID = new AtomicReference<>();
    static final Map<String, String> CREATED_REFERENCES = new ConcurrentHashMap<>();
    static final AtomicInteger PAYPAL_ORDER_SEQ = new AtomicInteger();
    static final HttpServer BACKENDS = backends();

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ServiceTokenProvider serviceTokens;

    @Autowired
    PaypalClient paypalClient;

    private static HttpServer backends() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/identity/token", exchange -> {
                TOKEN_CALLS.add(record(exchange));
                respond(exchange, 200,
                    "{\"access_token\":\"" + SERVICE_TOKEN + "\",\"expires_in\":300,\"token_type\":\"Bearer\"}");
            });
            server.createContext("/order/internal/orders/", exchange -> {
                Call call = record(exchange);
                String rest = call.path().substring("/order/internal/orders/".length());
                boolean pay = rest.endsWith("/payment");
                boolean inProgress = rest.endsWith("/payment-in-progress");
                long id = Long.parseLong(rest.replaceAll("/.*", ""));
                (pay ? ORDER_PAY_CALLS : inProgress ? ORDER_IN_PROGRESS_CALLS : ORDER_GET_CALLS).add(call);
                StubOrder order = ORDERS.get(id);
                if (ORDER_DOWN.get()) {
                    respond(exchange, 503, "");
                } else if (!("Bearer " + SERVICE_TOKEN).equals(call.authorization())) {
                    respond(exchange, 401, "");
                } else if (order == null) {
                    respond(exchange, 404, "{\"statusCode\":\"404 NOT_FOUND\",\"title\":\"Not Found\","
                        + "\"detail\":\"Order " + id + " is not found\",\"fieldErrors\":[]}");
                } else if (inProgress) {
                    // as order: an order already paid or ended cannot start a capture (decisions.md 5-62)
                    if ("COMPLETED".equals(order.paymentStatus())
                        || java.util.Set.of("CANCELLED", "REJECT", "REFUND").contains(order.orderStatus())) {
                        respond(exchange, 400, "{\"statusCode\":\"400 BAD_REQUEST\",\"title\":\"Bad Request\","
                            + "\"detail\":\"Order " + id + " cannot be paid (status " + order.orderStatus()
                            + ")\",\"fieldErrors\":[]}");
                    } else {
                        respond(exchange, 200, order.json(ORDER_PAYMENT_IDS.get(id)));
                    }
                } else if (!pay) {
                    respond(exchange, 200, order.json(ORDER_PAYMENT_IDS.get(id)));
                } else if (ORDER_PAY_STATUS.get() != 200) {
                    respond(exchange, ORDER_PAY_STATUS.get(), "{\"statusCode\":\"400 BAD_REQUEST\",\"title\":"
                        + "\"Bad Request\",\"detail\":\"Order " + id + " is already paid\",\"fieldErrors\":[]}");
                } else {
                    Long paymentId = ((Number) com.jayway.jsonpath.JsonPath.read(call.body(), "$.paymentId"))
                        .longValue();
                    ORDER_PAYMENT_IDS.put(id, paymentId);
                    StubOrder paid = new StubOrder(id, order.customerId(), order.totalPrice(), "PAID", "COMPLETED");
                    ORDERS.put(id, paid);
                    respond(exchange, 200, paid.json(paymentId));
                }
            });
            server.createContext("/payment-paypal/internal/status", exchange -> {
                record(exchange);
                respond(exchange, 200, "{\"configured\":" + PAYPAL_CONFIGURED.get() + ",\"currency\":\"USD\"}");
            });
            server.createContext("/payment-paypal/internal/orders", exchange -> {
                Call call = record(exchange);
                if (!PAYPAL_CONFIGURED.get()) {
                    respond(exchange, 503, "{\"statusCode\":\"503 SERVICE_UNAVAILABLE\",\"title\":\"Service "
                        + "Unavailable\",\"detail\":\"PayPal is not configured\",\"fieldErrors\":[]}");
                    return;
                }
                if (call.path().endsWith("/capture")) {
                    PAYPAL_CAPTURE_CALLS.add(call);
                    awaitGate(CAPTURE_GATE.get());
                    String id = call.path().substring("/payment-paypal/internal/orders/".length(),
                        call.path().length() - "/capture".length());
                    Answer answer = PAYPAL_CAPTURE_ANSWER.get();
                    if (answer != null) {
                        respond(exchange, answer.status(), answer.body());
                        return;
                    }
                    String customId = CAPTURED_CUSTOM_ID.get() != null ? CAPTURED_CUSTOM_ID.get()
                        : CREATED_REFERENCES.get(id);
                    respond(exchange, 200, "{\"paypalOrderId\":\"" + id + "\",\"status\":\"COMPLETED\","
                        + "\"captureId\":\"CAP" + id + "\",\"captureStatus\":\"COMPLETED\",\"amount\":"
                        + CAPTURED_AMOUNT.get() + ",\"currency\":\"" + CAPTURED_CURRENCY.get() + "\",\"customId\":\""
                        + customId + "\",\"paymentFee\":0.71}");
                    return;
                }
                PAYPAL_CREATE_CALLS.add(call);
                String id = "PP" + PAYPAL_ORDER_SEQ.incrementAndGet();
                CREATED_REFERENCES.put(id, com.jayway.jsonpath.JsonPath.read(call.body(), "$.reference"));
                awaitGate(CREATE_GATE.get());
                respond(exchange, 200, "{\"paypalOrderId\":\"" + id + "\",\"status\":\"PAYER_ACTION_REQUIRED\","
                    + "\"approvalUrl\":\"https://www.sandbox.paypal.com/checkoutnow?token=" + id + "\"}");
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void awaitGate(CountDownLatch gate) {
        if (gate == null) {
            return;
        }
        try {
            gate.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static Call record(HttpExchange exchange) throws IOException {
        return new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
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
        registry.add("shopapp.services.order", () -> base + "/order");
        registry.add("shopapp.services.payment-paypal", () -> base + "/payment-paypal");
        registry.add("shopapp.service-auth.token-uri", () -> base + "/identity/token");
        registry.add("shopapp.service-auth.client-id", () -> "payment-service");
        registry.add("shopapp.service-auth.client-secret", () -> "payment-secret");
        // the reconciler is run by the tests themselves
        registry.add("shopapp.payment.reconcile-interval", () -> "PT1H");
    }

    @BeforeEach
    void resetBackends() {
        ORDERS.clear();
        ORDER_PAYMENT_IDS.clear();
        ORDER_GET_CALLS.clear();
        ORDER_PAY_CALLS.clear();
        ORDER_IN_PROGRESS_CALLS.clear();
        CAPTURE_GATE.set(null);
        CREATE_GATE.set(null);
        ORDER_PAY_STATUS.set(200);
        ORDER_DOWN.set(false);
        TOKEN_CALLS.clear();
        PAYPAL_CREATE_CALLS.clear();
        PAYPAL_CAPTURE_CALLS.clear();
        PAYPAL_CONFIGURED.set(true);
        PAYPAL_CAPTURE_ANSWER.set(null);
        CAPTURED_AMOUNT.set("120.50");
        CAPTURED_CURRENCY.set("USD");
        CAPTURED_CUSTOM_ID.set(null);
        CREATED_REFERENCES.clear();
        serviceTokens.evict();
        paypalClient.forgetStatus();
        jdbc.execute("TRUNCATE payment RESTART IDENTITY");
        jdbc.update("update payment_provider set enabled = true, name = case id when 'COD' then 'Cash on Delivery'"
            + " else 'PayPal' end where id in ('COD', 'PAYPAL')");
        jdbc.update("delete from payment_provider where id not in ('COD', 'PAYPAL')");
    }

    static void order(long id, String customerId, String totalPrice, String orderStatus, String paymentStatus) {
        ORDERS.put(id, new StubOrder(id, customerId, totalPrice, orderStatus, paymentStatus));
    }

    static RequestPostProcessor customer(String id) {
        return jwt().jwt(token -> token.subject(id).tokenValue("token-" + id))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-1").tokenValue("token-admin"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    String paymentStatus(long paymentId) {
        return jdbc.queryForObject("select payment_status from payment where id = ?", String.class, paymentId);
    }

    BigDecimal paymentAmount(long paymentId) {
        return jdbc.queryForObject("select amount from payment where id = ?", BigDecimal.class, paymentId);
    }

    long countWithStatus(String status) {
        Long count = jdbc.queryForObject("select count(*) from payment where payment_status = ?", Long.class, status);
        return count == null ? 0 : count;
    }

    long paymentIdOf(String paypalOrderId) {
        return jdbc.queryForObject("select id from payment where payment_provider_checkout_id = ?", Long.class,
            paypalOrderId);
    }

    /** Waits up to 15 s for the condition. */
    static void await(String what, java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("timed out waiting for " + what);
            }
            Thread.sleep(20);
        }
    }

    long paymentCount() {
        Long count = jdbc.queryForObject("select count(*) from payment", Long.class);
        return count == null ? 0 : count;
    }
}
