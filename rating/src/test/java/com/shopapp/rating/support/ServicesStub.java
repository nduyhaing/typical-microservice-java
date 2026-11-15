package com.shopapp.rating.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Fake order ({@code /order}) and product ({@code /product}) services on a JDK HttpServer. Order answers
 * {@code completed?productId=} from the purchases registered with {@link #bought} for the caller's token; product
 * answers {@code list-featured?productId=} from {@link #product}. {@link #orderDown()} / {@link #productDown()} make
 * that service answer 503. Every request is recorded as {@code "METHOD uri | Authorization"}.
 */
public final class ServicesStub {

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Set<String> purchases = ConcurrentHashMap.newKeySet();
    private final Map<Long, String> products = new ConcurrentHashMap<>();
    private volatile boolean orderDown;
    private volatile boolean productDown;

    public ServicesStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        server.createContext("/order", this::handleOrder);
        server.createContext("/product", this::handleProduct);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String orderUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/order";
    }

    public String productUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/product";
    }

    /** The customer (token {@code token-<customerId>}) has an order with the product. */
    public void bought(String customerId, long productId) {
        purchases.add("Bearer token-" + customerId + "|" + productId);
    }

    public void product(long id, String name) {
        products.put(id, name);
    }

    public void orderDown() {
        orderDown = true;
    }

    public void productDown() {
        productDown = true;
    }

    public void reset() {
        requests.clear();
        purchases.clear();
        products.clear();
        orderDown = false;
        productDown = false;
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    private void handleOrder(HttpExchange exchange) throws IOException {
        String authorization = record(exchange);
        try (exchange) {
            if (orderDown) {
                send(exchange, 503, "{\"detail\":\"down\"}");
                return;
            }
            if (!exchange.getRequestURI().getPath().equals("/order/storefront/orders/completed")) {
                send(exchange, 404, "{}");
                return;
            }
            String productId = query(exchange).get("productId");
            boolean present = purchases.contains(authorization + "|" + productId);
            send(exchange, 200, "{\"isPresent\":" + present + "}");
        }
    }

    private void handleProduct(HttpExchange exchange) throws IOException {
        record(exchange);
        try (exchange) {
            if (productDown) {
                send(exchange, 503, "{\"detail\":\"down\"}");
                return;
            }
            if (!exchange.getRequestURI().getPath().equals("/product/storefront/products/list-featured")) {
                send(exchange, 404, "{}");
                return;
            }
            long id = Long.parseLong(query(exchange).get("productId"));
            String name = products.get(id);
            send(exchange, 200, name == null ? "[]"
                : "[{\"id\":" + id + ",\"name\":\"" + name + "\",\"slug\":\"p-" + id + "\",\"thumbnailUrl\":\"\","
                    + "\"price\":10.0,\"stockTrackingEnabled\":false,\"stockAvailable\":true,\"stockQuantity\":null}]");
        }
    }

    private String record(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " | " + authorization);
        return authorization;
    }

    private static Map<String, String> query(HttpExchange exchange) {
        Map<String, String> params = new HashMap<>();
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null) {
            return params;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
