package com.shopapp.tax.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.tax.PostgresContainerSupport;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.trace.v1.ResourceSpans;
import io.opentelemetry.proto.trace.v1.Span;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The shared telemetry set-up of common-library in a real service (decisions.md 6-1, 6-4): with an OTLP endpoint
 * configured, a request that carries a W3C {@code traceparent} (as order's RestClient sends it) is exported as a
 * server span of service "tax" in the caller's trace. The collector is a JDK HttpServer on the OTLP/HTTP path.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
class TelemetryExportIT {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final List<byte[]> TRACE_EXPORTS = new CopyOnWriteArrayList<>();
    private static final HttpServer COLLECTOR = startCollector();

    @Autowired
    private MockMvc mockMvc;

    private static HttpServer startCollector() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                if (exchange.getRequestURI().getPath().equals("/v1/traces")) {
                    TRACE_EXPORTS.add(exchange.getRequestBody().readAllBytes());
                }
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @DynamicPropertySource
    static void otlpEndpoint(DynamicPropertyRegistry registry) {
        String base = "http://localhost:" + COLLECTOR.getAddress().getPort();
        registry.add("management.opentelemetry.tracing.export.otlp.endpoint", () -> base + "/v1/traces");
        registry.add("management.opentelemetry.logging.export.otlp.endpoint", () -> base + "/v1/logs");
        registry.add("management.otlp.metrics.export.url", () -> base + "/v1/metrics");
        registry.add("management.opentelemetry.tracing.export.schedule-delay", () -> "100ms");
    }

    @AfterAll
    static void stopCollector() {
        COLLECTOR.stop(0);
    }

    private static List<Span> taxServerSpans() {
        return TRACE_EXPORTS.stream()
            .map(TelemetryExportIT::parse)
            .flatMap(request -> request.getResourceSpansList().stream())
            .filter(TelemetryExportIT::isTax)
            .flatMap(resource -> resource.getScopeSpansList().stream())
            .flatMap(scope -> scope.getSpansList().stream())
            .filter(span -> span.getKind() == Span.SpanKind.SPAN_KIND_SERVER)
            .toList();
    }

    private static boolean isTax(ResourceSpans resource) {
        return resource.getResource().getAttributesList().stream().anyMatch(attribute ->
            attribute.getKey().equals("service.name") && attribute.getValue().getStringValue().equals("tax"));
    }

    private static ExportTraceServiceRequest parse(byte[] body) {
        try {
            return ExportTraceServiceRequest.parseFrom(body);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @Test
    void requestWithTraceparent_isExportedAsServerSpanOfTheCallersTrace() throws Exception {
        mockMvc.perform(get("/backoffice/tax-rates/tax-percent")
                .param("taxClassId", "1").param("countryId", "1")
                .header("traceparent", "00-" + TRACE_ID + "-00f067aa0ba902b7-01")
                .with(jwt()))
            .andExpect(status().isOk());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(taxServerSpans())
            .anyMatch(span -> HexFormat.of().formatHex(span.getTraceId().toByteArray()).equals(TRACE_ID)));
    }
}
