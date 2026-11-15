package com.shopapp.product.support;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * A fake media service on a JDK HttpServer (context path {@code /media}): {@code GET /medias/{id}} and
 * {@code GET /medias?ids=} answer a media whose url is {@link #urlOf(long)}; every request URI is recorded.
 */
public final class MediaStub implements AutoCloseable {

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private volatile Duration delay = Duration.ZERO;
    private volatile Set<Long> missing = Set.of();

    public MediaStub() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        server.createContext("/media/medias", this::handle);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    /** The base URL to configure as {@code shopapp.services.media}. */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/media";
    }

    public static String urlOf(long id) {
        return "http://api.test/media/medias/" + id + "/file/img" + id + ".png";
    }

    public void delay(Duration value) {
        this.delay = value;
    }

    /** Ids the stub does not know (skipped by the list, 404 for a single get). */
    public void missing(Long... ids) {
        this.missing = Set.of(ids);
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    public void reset() {
        requests.clear();
        delay = Duration.ZERO;
        missing = Set.of();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String uri = exchange.getRequestURI().toString();
        requests.add(uri);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        try (exchange) {
            if (path.equals("/media/medias") && query != null && query.startsWith("ids=")) {
                List<String> items = new ArrayList<>();
                for (String id : query.substring(4).split(",")) {
                    long value = Long.parseLong(id);
                    if (!missing.contains(value)) {
                        items.add(json(value));
                    }
                }
                send(exchange, 200, "[" + String.join(",", items) + "]");
            } else if (path.startsWith("/media/medias/")) {
                long id = Long.parseLong(path.substring("/media/medias/".length()));
                if (missing.contains(id)) {
                    send(exchange, 404, "{\"statusCode\":\"404\"}");
                } else {
                    send(exchange, 200, json(id));
                }
            } else {
                send(exchange, 404, "{}");
            }
        }
    }

    /** Ids asked for by list requests, in request order. */
    public List<Long> listedIds() {
        return requests.stream()
            .filter(uri -> uri.startsWith("/media/medias?ids="))
            .flatMap(uri -> Arrays.stream(uri.substring("/media/medias?ids=".length()).split(",")))
            .map(Long::parseLong)
            .collect(Collectors.toList());
    }

    private static String json(long id) {
        return "{\"id\":" + id + ",\"caption\":\"c\",\"fileName\":\"img" + id + ".png\",\"mediaType\":\"image/png\","
            + "\"url\":\"" + urlOf(id) + "\"}";
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
