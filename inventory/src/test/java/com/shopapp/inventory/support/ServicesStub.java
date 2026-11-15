package com.shopapp.inventory.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Fake product ({@code /product}) and location ({@code /location}) services on a JDK HttpServer. Products are
 * registered with {@link #product}; addresses are kept in memory; {@link #productDown()} / {@link #locationDown()}
 * make that service answer 503. Every request is recorded as {@code "METHOD uri | Authorization"}. Product keeps a
 * sellable stock per product: {@code adjust-quantity} adds the deltas once per reference (like product, decisions.md
 * 3c-60) and {@link #sale} takes units like an order would.
 */
public final class ServicesStub implements AutoCloseable {

    /** A country id that location refuses with 400. */
    public static final long BAD_COUNTRY_ID = 999;

    private static final Pattern DELTA = Pattern.compile("\\{\"productId\":(\\d+),\"delta\":(-?\\d+)}");

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<String> adjustments = new CopyOnWriteArrayList<>();
    private final Set<String> appliedReferences = ConcurrentHashMap.newKeySet();
    private final Map<Long, Long> sellable = new ConcurrentHashMap<>();
    private volatile boolean dropNextAdjustResponse;
    private final Map<Long, String[]> products = new ConcurrentSkipListMap<>();
    private final Map<Long, String> addresses = new ConcurrentHashMap<>();
    private final AtomicLong addressIds = new AtomicLong();
    private volatile boolean productDown;
    private volatile boolean locationDown;

    public ServicesStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        server.createContext("/product", this::handleProduct);
        server.createContext("/location", this::handleLocation);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String productUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/product";
    }

    public String locationUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/location";
    }

    public void product(long id, String name, String sku, boolean stockTracked) {
        products.put(id, new String[] {name, sku, String.valueOf(stockTracked)});
    }

    /** An order took {@code quantity} units of the product's sellable stock. */
    public void sale(long id, long quantity) {
        sellable.merge(id, -quantity, Long::sum);
    }

    /** Product's sellable stock (0 until adjusted). */
    public long sellable(long id) {
        return sellable.getOrDefault(id, 0L);
    }

    /** The next adjust-quantity call is applied but its response is lost (connection closed). */
    public void dropNextAdjustResponse() {
        dropNextAdjustResponse = true;
    }

    public void productDown() {
        productDown = true;
    }

    public void locationDown() {
        locationDown = true;
    }

    public void reset() {
        requests.clear();
        adjustments.clear();
        appliedReferences.clear();
        sellable.clear();
        dropNextAdjustResponse = false;
        products.clear();
        addresses.clear();
        productDown = false;
        locationDown = false;
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    /** Bodies of {@code PUT /backoffice/products/adjust-quantity}, in order (retries included). */
    public List<String> adjustments() {
        return adjustments.stream().map(entry -> entry.substring(entry.indexOf(' ') + 1)).toList();
    }

    /** The {@code reference} of each adjust-quantity call, in order. */
    public List<String> adjustmentReferences() {
        return adjustments.stream().map(entry -> entry.substring(0, entry.indexOf(' '))).toList();
    }

    public Map<Long, String> addresses() {
        return Map.copyOf(addresses);
    }

    private void handleProduct(HttpExchange exchange) throws IOException {
        String body = record(exchange);
        try (exchange) {
            if (productDown) {
                send(exchange, 503, "{\"detail\":\"down\"}");
                return;
            }
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            if (method.equals("PUT") && path.equals("/product/backoffice/products/adjust-quantity")) {
                String reference = query(exchange).get("reference");
                adjustments.add(reference + " " + body);
                if (appliedReferences.add(reference)) {
                    Matcher item = DELTA.matcher(body);
                    while (item.find()) {
                        sellable.merge(Long.parseLong(item.group(1)), Long.parseLong(item.group(2)),
                            (current, delta) -> Math.max(current + delta, 0));
                    }
                }
                if (dropNextAdjustResponse) {
                    dropNextAdjustResponse = false;
                    return;
                }
                send(exchange, 204, null);
            } else if (method.equals("GET") && path.equals("/product/backoffice/products/for-warehouse")) {
                send(exchange, 200, forWarehouse(query(exchange)));
            } else if (method.equals("GET") && path.startsWith("/product/backoffice/products/")) {
                long id = Long.parseLong(path.substring("/product/backoffice/products/".length()));
                String[] product = products.get(id);
                if (product == null) {
                    send(exchange, 404, "{\"statusCode\":\"404\",\"detail\":\"Product " + id + " is not found\"}");
                } else {
                    send(exchange, 200, productJson(id, product));
                }
            } else {
                send(exchange, 404, "{}");
            }
        }
    }

    private String forWarehouse(Map<String, String> query) {
        String name = query.getOrDefault("name", "").toLowerCase();
        String sku = query.getOrDefault("sku", "").toLowerCase();
        String selection = query.getOrDefault("selection", "ALL");
        Set<Long> ids = query.containsKey("productIds") && !query.get("productIds").isEmpty()
            ? Arrays.stream(query.get("productIds").split(",")).map(Long::parseLong).collect(Collectors.toSet())
            : Set.of();
        List<String> items = new ArrayList<>();
        products.forEach((id, product) -> {
            boolean matches = product[0].toLowerCase().contains(name)
                && (sku.isEmpty() || product[1].toLowerCase().contains(sku));
            boolean selected = switch (selection) {
                case "YES" -> ids.contains(id);
                case "NO" -> !ids.contains(id);
                default -> true;
            };
            if (matches && selected) {
                items.add(productJson(id, product));
            }
        });
        return "[" + String.join(",", items) + "]";
    }

    private void handleLocation(HttpExchange exchange) throws IOException {
        String body = record(exchange);
        try (exchange) {
            if (locationDown) {
                send(exchange, 503, "{\"detail\":\"down\"}");
                return;
            }
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            String base = "/location/storefront/addresses";
            if (method.equals("POST") && path.equals(base)) {
                if (body.contains("\"countryId\":" + BAD_COUNTRY_ID)) {
                    send(exchange, 400, "{\"statusCode\":\"400 BAD_REQUEST\",\"title\":\"Bad Request\","
                        + "\"detail\":\"Country " + BAD_COUNTRY_ID + " is not found\",\"fieldErrors\":[]}");
                    return;
                }
                long id = addressIds.incrementAndGet();
                addresses.put(id, body);
                send(exchange, 200, withId(id, body));
                return;
            }
            if (!path.startsWith(base + "/")) {
                send(exchange, 404, "{}");
                return;
            }
            long id = Long.parseLong(path.substring(base.length() + 1));
            if (!addresses.containsKey(id)) {
                send(exchange, 404, "{\"detail\":\"Address " + id + " is not found\"}");
            } else if (method.equals("GET")) {
                send(exchange, 200, withId(id, addresses.get(id)));
            } else if (method.equals("PUT")) {
                addresses.put(id, body);
                send(exchange, 204, null);
            } else if (method.equals("DELETE")) {
                addresses.remove(id);
                send(exchange, 200, null);
            } else {
                send(exchange, 405, "{}");
            }
        }
    }

    private String record(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " | "
            + exchange.getRequestHeaders().getFirst("Authorization"));
        return body;
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

    private String productJson(long id, String[] product) {
        return "{\"id\":" + id + ",\"name\":\"" + product[0] + "\",\"sku\":\"" + product[1]
            + "\",\"stockTrackingEnabled\":" + product[2] + ",\"stockQuantity\":" + sellable(id) + "}";
    }

    private static String withId(long id, String body) {
        return "{\"id\":" + id + "," + body.substring(body.indexOf('{') + 1);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
