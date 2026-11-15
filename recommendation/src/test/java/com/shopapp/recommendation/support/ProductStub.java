package com.shopapp.recommendation.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Fake product service on a JDK HttpServer, answering like product: {@code GET /product/storefront/products-es/{id}}
 * and {@code GET /product/storefront/product/{slug}} (404 for unknown, unpublished or hidden products) and
 * {@code GET /product/backoffice/products} (ids, needs a bearer token). {@link #down(long)} makes one product's
 * products-es answer 503. Requests are recorded.
 */
public final class ProductStub {

    /** One stubbed product. */
    public record StubProduct(long id, String name, String brand, List<String> categories, String shortDescription,
                              Instant modified) {

        String slug() {
            return name.toLowerCase().replace(' ', '-');
        }
    }

    private final HttpServer server;
    private final Map<Long, StubProduct> products = new ConcurrentSkipListMap<>();
    private final Map<Long, Boolean> down = new ConcurrentHashMap<>();
    private final List<String> requests = new CopyOnWriteArrayList<>();

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

    /** A published, individually visible product. */
    public void product(long id, String name, String brand, List<String> categories, String shortDescription,
                        Instant modified) {
        products.put(id, new StubProduct(id, name, brand, categories, shortDescription, modified));
    }

    /** Product answers 404 for it (deleted, unpublished or hidden: product's public endpoints hide them). */
    public void remove(long id) {
        products.remove(id);
    }

    public void down(long id) {
        down.put(id, true);
    }

    public void up(long id) {
        down.remove(id);
    }

    public void reset() {
        products.clear();
        down.clear();
        requests.clear();
    }

    public long summaryRequests(long id) {
        return requests.stream().filter(request -> request.equals("GET /product/storefront/products-es/" + id))
            .count();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(exchange.getRequestMethod() + " " + path);
        try (exchange) {
            if (path.startsWith("/product/storefront/products-es/")) {
                long id = Long.parseLong(path.substring("/product/storefront/products-es/".length()));
                StubProduct product = products.get(id);
                if (down.containsKey(id)) {
                    send(exchange, 503, "{\"detail\":\"down\"}");
                } else if (product != null) {
                    send(exchange, 200, summary(product));
                } else {
                    notFound(exchange, String.valueOf(id));
                }
            } else if (path.startsWith("/product/storefront/product/")) {
                String slug = path.substring("/product/storefront/product/".length());
                StubProduct product = products.values().stream().filter(p -> p.slug().equals(slug)).findFirst()
                    .orElse(null);
                if (product == null) {
                    notFound(exchange, slug);
                } else {
                    send(exchange, 200, detail(product));
                }
            } else if (path.equals("/product/backoffice/products")) {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                if (auth == null || !auth.startsWith("Bearer ")) {
                    send(exchange, 401, "{}");
                    return;
                }
                String items = products.keySet().stream().map(id -> "{\"id\":" + id + "}")
                    .collect(Collectors.joining(","));
                send(exchange, 200, "{\"productContent\":[" + items + "],\"pageNo\":0,\"pageSize\":100,"
                    + "\"totalElements\":" + products.size() + ",\"totalPages\":1,\"isLast\":true}");
            } else {
                send(exchange, 404, "{}");
            }
        }
    }

    private static String summary(StubProduct p) {
        return """
            {"id":%d,"name":"%s","slug":"%s","price":%s,"isPublished":true,"isVisibleIndividually":true,
             "isAllowedToOrder":true,"isFeatured":false,"thumbnailMediaId":null,
             "thumbnailUrl":"http://media/%d.png","brand":%s,"categories":%s,"attributes":[],
             "createdOn":"%s","lastModifiedOn":"%s"}
            """.formatted(p.id(), p.name(), p.slug(), 100 + p.id(), p.id(), quoted(p.brand()), list(p.categories()),
            Instant.parse("2026-01-01T00:00:00Z"), p.modified());
    }

    private static String detail(StubProduct p) {
        return """
            {"id":%d,"name":"%s","brandName":%s,"productCategories":%s,
             "productAttributeGroups":[{"name":"General","productAttributeValues":[{"name":"Type","value":"%s"}]}],
             "shortDescription":%s,"description":"long text","specification":null,"price":1}
            """.formatted(p.id(), p.name(), quoted(p.brand()), list(p.categories()),
            p.categories().isEmpty() ? "Other" : p.categories().getFirst(), quoted(p.shortDescription()));
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String list(List<String> values) {
        return values.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(",", "[", "]"));
    }

    private static void notFound(HttpExchange exchange, String key) throws IOException {
        send(exchange, 404, "{\"statusCode\":\"404\",\"detail\":\"Product " + key + " is not found\"}");
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
