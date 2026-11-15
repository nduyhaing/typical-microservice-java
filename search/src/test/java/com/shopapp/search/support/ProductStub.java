package com.shopapp.search.support;

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
 * Fake product service on a JDK HttpServer: {@code GET /product/storefront/products-es/{id}} answers like product
 * (404 for unknown, unpublished or hidden products), {@code GET /product/backoffice/products} lists the ids.
 * {@link #down(long)} makes one product's detail answer 503. Requests are recorded.
 */
public final class ProductStub {

    private final HttpServer server;
    private final Map<Long, String> details = new ConcurrentSkipListMap<>();
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
    public void product(long id, String name, double price, String brand, List<String> categories,
                        List<String> attributes, Instant modified) {
        details.put(id, json(id, name, price, brand, categories, attributes, modified));
    }

    /** Product answers 404 for it (deleted, unpublished or hidden: product's public endpoint hides them). */
    public void remove(long id) {
        details.remove(id);
    }

    public void down(long id) {
        down.put(id, true);
    }

    public void up(long id) {
        down.remove(id);
    }

    public void reset() {
        details.clear();
        down.clear();
        requests.clear();
    }

    public long detailRequests(long id) {
        return requests.stream().filter(request -> request.equals("GET /product/storefront/products-es/" + id))
            .count();
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        requests.add(exchange.getRequestMethod() + " " + path);
        try (exchange) {
            if (path.startsWith("/product/storefront/products-es/")) {
                long id = Long.parseLong(path.substring("/product/storefront/products-es/".length()));
                if (down.containsKey(id)) {
                    send(exchange, 503, "{\"detail\":\"down\"}");
                } else if (details.containsKey(id)) {
                    send(exchange, 200, details.get(id));
                } else {
                    send(exchange, 404, "{\"statusCode\":\"404\",\"detail\":\"Product " + id + " is not found\"}");
                }
            } else if (path.equals("/product/backoffice/products")) {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                if (auth == null || !auth.startsWith("Bearer ")) {
                    send(exchange, 401, "{}");
                    return;
                }
                String items = details.keySet().stream().map(id -> "{\"id\":" + id + "}")
                    .collect(Collectors.joining(","));
                send(exchange, 200, "{\"productContent\":[" + items + "],\"pageNo\":0,\"pageSize\":100,"
                    + "\"totalElements\":" + details.size() + ",\"totalPages\":1,\"isLast\":true}");
            } else {
                send(exchange, 404, "{}");
            }
        }
    }

    private static String json(long id, String name, double price, String brand, List<String> categories,
                               List<String> attributes, Instant modified) {
        return """
            {"id":%d,"name":"%s","slug":"%s","price":%s,"isPublished":true,"isVisibleIndividually":true,
             "isAllowedToOrder":true,"isFeatured":false,"thumbnailMediaId":null,"thumbnailUrl":"",
             "brand":%s,"categories":%s,"attributes":%s,"createdOn":"%s","lastModifiedOn":"%s"}
            """.formatted(id, name, name.toLowerCase().replace(' ', '-'), price,
            brand == null ? "null" : "\"" + brand + "\"", list(categories), list(attributes),
            Instant.parse("2026-01-01T00:00:00Z").plusSeconds(id), modified);
    }

    private static String list(List<String> values) {
        return values.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(",", "[", "]"));
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
