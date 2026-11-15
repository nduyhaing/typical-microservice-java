package com.shopapp.commonlibrary.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.trace.v1.ExportTraceServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.trace.v1.Span;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts a small servlet app with the shared telemetry set-up and a fake OTLP/HTTP receiver (JDK HttpServer): a
 * request is exported as one trace (server span, RestClient span, downstream server span) with its log lines; with
 * no endpoint nothing is exported; with the collector down requests are not slowed down or failed.
 */
@ExtendWith(OutputCaptureExtension.class)
class OtlpExportTest {

    private static final String[] NO_DATABASE_NO_SECURITY = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web"
            + ".OAuth2ResourceServerWebSecurityAutoConfiguration",
    };

    private final Map<String, List<byte[]>> received = new ConcurrentHashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private HttpServer receiver;
    private ConfigurableApplicationContext context;

    // scans common-library like every service does (scanBasePackages), so the auto-configuration class is also
    // picked up as a plain configuration class
    @SpringBootApplication(scanBasePackages = "com.shopapp.commonlibrary")
    @RestController
    @Slf4j
    static class App {

        private final Environment environment;

        App(Environment environment) {
            this.environment = environment;
        }

        @GetMapping("/hello")
        String hello() {
            log.info("hello handled");
            return "hi";
        }

        @GetMapping("/call")
        String call() {
            log.info("calling hello");
            return ObservedRestClient.builder()
                .baseUrl("http://localhost:" + environment.getProperty("local.server.port"))
                .build()
                .get().uri("/hello").retrieve().body(String.class);
        }
    }

    @BeforeEach
    void startReceiver() throws IOException {
        receiver = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        for (String path : List.of("/v1/traces", "/v1/logs", "/v1/metrics")) {
            receiver.createContext(path, exchange -> {
                received.computeIfAbsent(path, p -> new CopyOnWriteArrayList<>())
                    .add(exchange.getRequestBody().readAllBytes());
                exchange.getResponseHeaders().add("Content-Type", "application/x-protobuf");
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
            });
        }
        receiver.start();
    }

    @AfterEach
    void stop() {
        if (context != null) {
            context.close();
        }
        receiver.stop(0);
    }

    private ConfigurableApplicationContext start(String... properties) {
        return new SpringApplicationBuilder(App.class)
            .properties("server.port=0", "spring.application.name=otel-test",
                "spring.autoconfigure.exclude=" + String.join(",", NO_DATABASE_NO_SECURITY))
            .run(Stream.of(properties).map(property -> "--" + property).toArray(String[]::new));
    }

    private ConfigurableApplicationContext startExportingTo(String baseUrl) {
        return start(
            "management.opentelemetry.tracing.export.otlp.endpoint=" + baseUrl + "/v1/traces",
            "management.opentelemetry.logging.export.otlp.endpoint=" + baseUrl + "/v1/logs",
            "management.otlp.metrics.export.url=" + baseUrl + "/v1/metrics",
            "management.opentelemetry.tracing.export.schedule-delay=100ms",
            "management.opentelemetry.logging.export.schedule-delay=100ms",
            "management.otlp.metrics.export.step=1s");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create(
                "http://localhost:" + context.getEnvironment().getProperty("local.server.port") + path))
                .timeout(Duration.ofSeconds(5)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private List<Span> exportedSpans() {
        return received.getOrDefault("/v1/traces", List.of()).stream()
            .map(body -> parse(() -> ExportTraceServiceRequest.parseFrom(body)))
            .flatMap(request -> request.getResourceSpansList().stream())
            .filter(resource -> hasServiceName(resource.getResource().getAttributesList()))
            .flatMap(resource -> resource.getScopeSpansList().stream())
            .flatMap(scope -> scope.getSpansList().stream())
            .toList();
    }

    private List<LogRecord> exportedLogs() {
        return received.getOrDefault("/v1/logs", List.of()).stream()
            .map(body -> parse(() -> ExportLogsServiceRequest.parseFrom(body)))
            .flatMap(request -> request.getResourceLogsList().stream())
            .filter(resource -> hasServiceName(resource.getResource().getAttributesList()))
            .flatMap(resource -> resource.getScopeLogsList().stream())
            .flatMap(scope -> scope.getLogRecordsList().stream())
            .toList();
    }

    private static boolean hasServiceName(List<KeyValue> attributes) {
        return attributes.stream().anyMatch(attribute -> attribute.getKey().equals("service.name")
            && attribute.getValue().getStringValue().equals("otel-test"));
    }

    private interface Parser<T> {
        T parse() throws IOException;
    }

    private static <T> T parse(Parser<T> parser) {
        try {
            return parser.parse();
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Stream<OpenTelemetryAppender> otelAppenders() {
        Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);
        Stream.Builder<OpenTelemetryAppender> appenders = Stream.builder();
        root.iteratorForAppenders().forEachRemaining(appender -> {
            if (appender instanceof OpenTelemetryAppender otel) {
                appenders.add(otel);
            }
        });
        return appenders.build();
    }

    @Test
    void request_isExportedAsOneTraceAcrossRestClient_withItsLogLines(CapturedOutput output) throws Exception {
        context = startExportingTo("http://localhost:" + receiver.getAddress().getPort());

        assertThat(get("/call").body()).isEqualTo("hi");

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<Span> spans = exportedSpans();
            assertThat(spans).anyMatch(span -> span.getKind() == Span.SpanKind.SPAN_KIND_CLIENT);
            assertThat(spans.stream().filter(span -> span.getKind() == Span.SpanKind.SPAN_KIND_SERVER))
                .hasSizeGreaterThanOrEqualTo(2);
        });
        List<Span> spans = exportedSpans();
        assertThat(spans.stream().map(Span::getTraceId).distinct()).hasSize(1);
        String traceId = HexFormat.of().formatHex(spans.getFirst().getTraceId().toByteArray());

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(exportedLogs())
            .anyMatch(log -> log.getBody().getStringValue().equals("hello handled")
                && HexFormat.of().formatHex(log.getTraceId().toByteArray()).equals(traceId)));

        // the console line carries the same trace id (log correlation pattern)
        assertThat(output.getOut().lines().filter(line -> line.contains("hello handled")))
            .anyMatch(line -> line.contains(traceId));

        await().atMost(Duration.ofSeconds(10)).until(() -> !received.getOrDefault("/v1/metrics", List.of()).isEmpty());
    }

    @Test
    void withoutEndpoint_nothingIsExported_butLogLinesStillCarryTraceIds(CapturedOutput output) throws Exception {
        context = start();

        assertThat(get("/hello").body()).isEqualTo("hi");

        assertThat(context.getBeansOfType(SpanExporter.class)).isEmpty();
        assertThat(otelAppenders()).isEmpty();
        assertThat(context.getEnvironment().getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
        assertThat(output.getOut().lines().filter(line -> line.contains("hello handled")))
            .anyMatch(line -> line.matches(".*\\[[0-9a-f]{32}-[0-9a-f]{16}].*"));
        assertThat(received).isEmpty();
    }

    @Test
    void collectorDown_requestsStillSucceedQuickly() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        context = startExportingTo("http://localhost:" + closedPort);
        assertThat(otelAppenders()).hasSize(1);

        for (int i = 0; i < 20; i++) {
            long started = System.nanoTime();
            HttpResponse<String> response = get("/call");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        }
        Thread.sleep(500);
        assertThat(get("/hello").statusCode()).isEqualTo(200);

        long closing = System.nanoTime();
        context.close();
        context = null;
        assertThat(Duration.ofNanos(System.nanoTime() - closing)).isLessThan(Duration.ofSeconds(15));
        assertThat(otelAppenders()).isEmpty();
    }
}
