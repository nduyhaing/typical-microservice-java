package com.shopapp.payment.paypal.controller;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A JDK HttpServer standing in for the PayPal REST API: OAuth token, create order, capture order. Records every
 * request; the answers can be changed per test.
 */
final class PaypalStub {

    /** A request the stub received. */
    record Call(String method, String path, String body, String authorization, String requestId) {
    }

    /** A stubbed answer. */
    record Answer(int status, String body) {
    }

    static final List<Call> TOKEN_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> CREATE_CALLS = new CopyOnWriteArrayList<>();
    static final List<Call> CAPTURE_CALLS = new CopyOnWriteArrayList<>();
    /** How many tokens were handed out: token N is "paypal-token-N". */
    static final AtomicInteger TOKENS_ISSUED = new AtomicInteger();
    /** Answer of create order; null = a normal created order with an approval link. */
    static final AtomicReference<Answer> CREATE_ANSWER = new AtomicReference<>();
    /** Answer of capture; null = a completed capture of 12.34 USD. */
    static final AtomicReference<Answer> CAPTURE_ANSWER = new AtomicReference<>();
    /** Answer this many create calls with 401 first (expired token). */
    static final AtomicInteger CREATE_UNAUTHORIZED = new AtomicInteger();

    static final HttpServer SERVER = start();

    private PaypalStub() {
    }

    static String baseUrl() {
        return "http://127.0.0.1:" + SERVER.getAddress().getPort();
    }

    static void reset() {
        TOKEN_CALLS.clear();
        CREATE_CALLS.clear();
        CAPTURE_CALLS.clear();
        CREATE_ANSWER.set(null);
        CAPTURE_ANSWER.set(null);
        CREATE_UNAUTHORIZED.set(0);
    }

    static String completedCapture(String orderId, String value, String currency, String customId) {
        return """
            {"id":"%s","status":"COMPLETED","purchase_units":[{"reference_id":"%s","payments":{"captures":[
              {"id":"CAPTURE-%s","status":"COMPLETED","amount":{"currency_code":"%s","value":"%s"},
               "custom_id":"%s","seller_receivable_breakdown":{"gross_amount":{"currency_code":"%s","value":"%s"},
               "paypal_fee":{"currency_code":"%s","value":"0.71"}}}]}}]}"""
            .formatted(orderId, customId, orderId, currency, value, customId, currency, value, currency);
    }

    private static HttpServer start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());
            server.createContext("/v1/oauth2/token", exchange -> {
                TOKEN_CALLS.add(record(exchange));
                int n = TOKENS_ISSUED.incrementAndGet();
                respond(exchange, 200, "{\"scope\":\"x\",\"access_token\":\"paypal-token-" + n
                    + "\",\"token_type\":\"Bearer\",\"app_id\":\"APP-1\",\"expires_in\":32400,\"nonce\":\"n\"}");
            });
            server.createContext("/v2/checkout/orders", exchange -> {
                Call call = record(exchange);
                String path = call.path();
                if (path.endsWith("/capture")) {
                    CAPTURE_CALLS.add(call);
                    String id = path.substring("/v2/checkout/orders/".length(), path.length() - "/capture".length());
                    Answer answer = CAPTURE_ANSWER.get();
                    if (answer == null) {
                        respond(exchange, 201, completedCapture(id, "12.34", "USD", "payment-7"));
                    } else {
                        respond(exchange, answer.status(), answer.body());
                    }
                    return;
                }
                CREATE_CALLS.add(call);
                if (CREATE_UNAUTHORIZED.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                    respond(exchange, 401, "{\"error\":\"invalid_token\"}");
                    return;
                }
                Answer answer = CREATE_ANSWER.get();
                if (answer != null) {
                    respond(exchange, answer.status(), answer.body());
                    return;
                }
                respond(exchange, 200, """
                    {"id":"5O190127TN364715T","status":"PAYER_ACTION_REQUIRED","links":[
                      {"href":"https://api-m.sandbox.paypal.com/v2/checkout/orders/5O190127TN364715T","rel":"self",
                       "method":"GET"},
                      {"href":"https://www.sandbox.paypal.com/checkoutnow?token=5O190127TN364715T",
                       "rel":"payer-action","method":"GET"}]}""");
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Call record(HttpExchange exchange) throws IOException {
        return new Call(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
            exchange.getRequestHeaders().getFirst("Authorization"),
            exchange.getRequestHeaders().getFirst("PayPal-Request-Id"));
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
}
