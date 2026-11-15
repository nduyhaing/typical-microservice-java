package com.shopapp.commonlibrary.observability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Shared telemetry defaults for every service (decisions.md 6-1, 6-3). Added as the lowest-precedence property
 * source, so a service's own properties and environment always win.
 *
 * <p>Export is <b>off unless an OTLP endpoint is configured</b> ({@code OTEL_EXPORTER_OTLP_ENDPOINT}, which Spring
 * Boot maps to the traces/logs/metrics endpoints, or one of the {@code management.*} endpoint properties). Traces and
 * logs need an endpoint anyway; Micrometer's OTLP registry would push to {@code localhost:4318} by default, so it is
 * switched off here. Tracing itself stays on (sampled 100 %), so log lines always carry trace and span ids.
 */
public class ObservabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "shopappObservabilityDefaults";

    private static final List<String> ENDPOINT_PROPERTIES = List.of(
        "OTEL_EXPORTER_OTLP_ENDPOINT",
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT",
        "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT",
        "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT",
        "management.opentelemetry.tracing.export.otlp.endpoint",
        "management.opentelemetry.logging.export.otlp.endpoint",
        "management.otlp.metrics.export.url");

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        // dev: keep every trace; override with management.tracing.sampling.probability or OTEL_TRACES_SAMPLER_ARG
        defaults.put("management.tracing.sampling.probability", "1.0");
        // Kafka producer/consumer spans and trace context in record headers
        defaults.put("spring.kafka.template.observation-enabled", "true");
        defaults.put("spring.kafka.listener.observation-enabled", "true");
        // trace ids in the MDC of reactive (WebFlux / gateway) log lines, also with export off (decisions.md 6-65)
        defaults.put("spring.reactor.context-propagation", "auto");

        if (otlpConfigured(environment)) {
            defaults.put("management.otlp.metrics.export.step", "15s");
            defaults.put("management.otlp.metrics.export.base-time-unit", "seconds");
            defaults.put("management.metrics.distribution.percentiles-histogram.http.server.requests", "true");
            // ~20 latency buckets (5 ms .. 10 s) instead of ~70: fewer series to push and store
            defaults.put("management.metrics.distribution.minimum-expected-value.http.server.requests", "5ms");
            defaults.put("management.metrics.distribution.maximum-expected-value.http.server.requests", "10s");
            defaults.put("management.opentelemetry.tracing.export.otlp.connect-timeout", "2s");
            defaults.put("management.opentelemetry.logging.export.otlp.connect-timeout", "2s");
        } else {
            defaults.put("management.otlp.metrics.export.enabled", "false");
        }
        environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults));
    }

    static boolean otlpConfigured(ConfigurableEnvironment environment) {
        return ENDPOINT_PROPERTIES.stream().anyMatch(name -> StringUtils.hasText(environment.getProperty(name)));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
