package com.shopapp.promotion.support;

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
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Fake product service ({@code /product}) on a JDK HttpServer with the endpoint promotion calls,
 * {@code GET /storefront/products/promotion-targets?ids=} (decisions.md 4-74), plus the older list endpoints (to show
 * they are no longer called). {@link #down()} answers 503.
 * Every request is recorded as {@code "GET uri | Authorization"}.
 */
public final class ProductStub implements AutoCloseable {

    private record Product(long id, Long parentId, Long brandId, double price, Set<Long> categoryIds) {
    }

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<Long, Product> products = new ConcurrentSkipListMap<>();
    private final Map<Long, String> brands = new ConcurrentSkipListMap<>();
    private final Map<Long, String> categories = new ConcurrentSkipListMap<>();
    private volatile boolean down;

    public ProductStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        server.createContext("/product", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/product";
    }

    /** An orderable product; {@code parentId} set for a variation; categories only matter for parents. */
    public void product(long id, Long parentId, Long brandId, double price, Long... categoryIds) {
        products.put(id, new Product(id, parentId, brandId, price, Set.of(categoryIds)));
    }

    public void brand(long id, String slug) {
        brands.put(id, slug);
    }

    public void category(long id, String slug) {
        categories.put(id, slug);
    }

    public void down() {
        down = true;
    }

    public void reset() {
        requests.clear();
        products.clear();
        brands.clear();
        categories.clear();
        down = false;
    }

    public void resetRequests() {
        requests.clear();
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " | "
            + exchange.getRequestHeaders().getFirst("Authorization"));
        try (exchange) {
            if (down) {
                send(exchange, 503, "{\"detail\":\"down\"}");
                return;
            }
            String path = exchange.getRequestURI().getPath().substring("/product".length());
            Map<String, String> query = query(exchange);
            if (path.equals("/storefront/products/promotion-targets")) {
                Set<Long> ids = Arrays.stream(query.get("ids").split(",")).map(Long::parseLong)
                    .collect(Collectors.toSet());
                String items = products.values().stream().filter(p -> ids.contains(p.id()))
                    .map(this::targetJson).collect(Collectors.joining(","));
                send(exchange, 200, "[" + items + "]");
            } else if (path.equals("/products")) {
                if (exchange.getRequestHeaders().getFirst("Authorization") == null) {
                    send(exchange, 401, "{}");
                    return;
                }
                Set<Long> ids = Arrays.stream(query.get("ids").split(",")).map(Long::parseLong)
                    .collect(Collectors.toSet());
                String items = products.values().stream().filter(p -> ids.contains(p.id()))
                    .map(p -> "{\"id\":" + p.id() + ",\"name\":\"P" + p.id() + "\",\"parentId\":" + p.parentId()
                        + ",\"brandId\":" + p.brandId() + ",\"price\":" + p.price() + ",\"taxClassId\":null}")
                    .collect(Collectors.joining(","));
                send(exchange, 200, "{\"productCheckoutListVms\":[" + items + "],\"pageNo\":0,\"pageSize\":100,"
                    + "\"totalElements\":0,\"totalPages\":1,\"isLast\":true}");
            } else if (path.equals("/storefront/brands")) {
                send(exchange, 200, slugList(brands));
            } else if (path.equals("/storefront/categories")) {
                send(exchange, 200, slugList(categories));
            } else if (path.startsWith("/storefront/brand/") && path.endsWith("/products")) {
                Long brandId = idOf(brands, path, "/storefront/brand/");
                String items = products.values().stream()
                    .filter(p -> p.parentId() == null && brandId != null && brandId.equals(p.brandId()))
                    .map(p -> "{\"id\":" + p.id() + ",\"name\":\"P" + p.id() + "\"}")
                    .collect(Collectors.joining(","));
                send(exchange, 200, "[" + items + "]");
            } else if (path.startsWith("/storefront/category/") && path.endsWith("/products")) {
                Long categoryId = idOf(categories, path, "/storefront/category/");
                int pageNo = Integer.parseInt(query.getOrDefault("pageNo", "0"));
                int pageSize = Integer.parseInt(query.getOrDefault("pageSize", "2"));
                List<Product> inCategory = products.values().stream()
                    .filter(p -> p.parentId() == null && p.categoryIds().contains(categoryId)).toList();
                int totalPages = (inCategory.size() + pageSize - 1) / pageSize;
                String items = inCategory.stream().skip((long) pageNo * pageSize).limit(pageSize)
                    .map(p -> "{\"id\":" + p.id() + ",\"name\":\"P" + p.id() + "\"}")
                    .collect(Collectors.joining(","));
                send(exchange, 200, "{\"productContent\":[" + items + "],\"pageNo\":" + pageNo + ",\"pageSize\":"
                    + pageSize + ",\"totalElements\":" + inCategory.size() + ",\"totalPages\":" + totalPages
                    + ",\"isLast\":" + (pageNo + 1 >= totalPages) + "}");
            } else {
                send(exchange, 404, "{}");
            }
        }
    }

    /** Like product: a variation's brand falls back to its parent's, its categories include its parent's. */
    private String targetJson(Product product) {
        Product parent = product.parentId() == null ? null : products.get(product.parentId());
        Long brandId = product.brandId() != null || parent == null ? product.brandId() : parent.brandId();
        Set<Long> categoryIds = new java.util.TreeSet<>(product.categoryIds());
        if (parent != null) {
            categoryIds.addAll(parent.categoryIds());
        }
        return "{\"id\":" + product.id() + ",\"parentId\":" + product.parentId() + ",\"brandId\":" + brandId
            + ",\"categoryIds\":" + categoryIds.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"))
            + ",\"price\":" + product.price() + "}";
    }

    private static String slugList(Map<Long, String> items) {
        List<String> json = new ArrayList<>();
        items.forEach((id, slug) -> json.add("{\"id\":" + id + ",\"name\":\"" + slug + "\",\"slug\":\"" + slug
            + "\",\"parentId\":-1}"));
        return "[" + String.join(",", json) + "]";
    }

    private static Long idOf(Map<Long, String> items, String path, String prefix) {
        String slug = path.substring(prefix.length(), path.length() - "/products".length());
        return items.entrySet().stream().filter(e -> e.getValue().equals(slug)).map(Map.Entry::getKey)
            .findFirst().orElse(null);
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

    @Override
    public void close() {
        server.stop(0);
    }
}
