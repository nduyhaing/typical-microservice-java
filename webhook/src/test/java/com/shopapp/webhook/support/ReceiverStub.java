package com.shopapp.webhook.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * A webhook receiver (JDK HttpServer on 127.0.0.1, any path): records every request; answers 200 unless told
 * otherwise with a queue of status codes (the last one repeats), a delay or a redirect.
 */
public final class ReceiverStub {

    /** One received request. */
    public record Request(String path, Map<String, String> headers, byte[] body) {

        /** A header, case-insensitively. */
        public String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        public String bodyText() {
            return new String(body, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Deque<Integer> statuses = new ConcurrentLinkedDeque<>();
    private volatile int lastStatus = 200;
    private volatile long delayMs;
    private volatile String redirect;
    private boolean stopped;

    private ReceiverStub(HttpServer server) {
        this.server = server;
    }

    public static ReceiverStub start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        ReceiverStub stub = new ReceiverStub(server);
        server.createContext("/", exchange -> {
            byte[] body;
            try (InputStream in = exchange.getRequestBody()) {
                body = in.readAllBytes();
            }
            Map<String, String> headers = new TreeMap<>();
            exchange.getRequestHeaders().forEach((name, values) ->
                headers.put(name.toLowerCase(Locale.ROOT), values.getFirst()));
            stub.requests.add(new Request(exchange.getRequestURI().getPath(), headers, body));
            try {
                if (stub.delayMs > 0) {
                    Thread.sleep(stub.delayMs);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            int status;
            if (stub.redirect != null) {
                exchange.getResponseHeaders().add("Location", stub.redirect);
                status = 302;
            } else {
                Integer next = stub.statuses.poll();
                status = next != null ? next : stub.lastStatus;
            }
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();
        return stub;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    /** Answers these codes in turn, then keeps answering the last one. */
    public void respond(int... codes) {
        statuses.clear();
        for (int i = 0; i < codes.length - 1; i++) {
            statuses.add(codes[i]);
        }
        lastStatus = codes[codes.length - 1];
    }

    public void delay(long millis) {
        delayMs = millis;
    }

    public void redirectTo(String location) {
        redirect = location;
    }

    public List<Request> requests() {
        return new ArrayList<>(requests);
    }

    public void reset() {
        requests.clear();
        statuses.clear();
        lastStatus = 200;
        delayMs = 0;
        redirect = null;
    }

    public synchronized void stop() {
        if (!stopped) {
            stopped = true;
            server.stop(0);
        }
    }
}
