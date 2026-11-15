package com.shopapp.commonlibrary.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ObservabilityEnvironmentPostProcessorTest {

    private final ObservabilityEnvironmentPostProcessor postProcessor = new ObservabilityEnvironmentPostProcessor();

    private MockEnvironment process(MockEnvironment environment) {
        postProcessor.postProcessEnvironment(environment, new SpringApplication());
        return environment;
    }

    @Test
    void withoutOtlpEndpoint_metricsExportIsOff_soNothingPushesToLocalhost() {
        MockEnvironment environment = process(new MockEnvironment());

        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.reactor.context-propagation")).isNull();
    }

    @Test
    void always_samplesEveryTraceAndObservesKafka() {
        MockEnvironment environment = process(new MockEnvironment());

        assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("1.0");
        assertThat(environment.getProperty("spring.kafka.template.observation-enabled")).isEqualTo("true");
        assertThat(environment.getProperty("spring.kafka.listener.observation-enabled")).isEqualTo("true");
    }

    @Test
    void withOtelEnvironmentVariable_metricsExportStaysOnWithShortStepAndSeconds() {
        MockEnvironment environment = process(
            new MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://otel-collector:4318"));

        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isNull();
        assertThat(environment.getProperty("management.otlp.metrics.export.step")).isEqualTo("15s");
        assertThat(environment.getProperty("management.otlp.metrics.export.base-time-unit")).isEqualTo("seconds");
        assertThat(environment.getProperty("spring.reactor.context-propagation")).isEqualTo("auto");
        assertThat(environment.getProperty(
            "management.metrics.distribution.maximum-expected-value.http.server.requests")).isEqualTo("10s");
    }

    @Test
    void withExplicitTracingEndpointProperty_countsAsConfigured() {
        MockEnvironment environment = process(new MockEnvironment()
            .withProperty("management.opentelemetry.tracing.export.otlp.endpoint", "http://c:4318/v1/traces"));

        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isNull();
    }

    @Test
    void blankEndpoint_countsAsNotConfigured() {
        MockEnvironment environment = process(new MockEnvironment().withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", " "));

        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("false");
    }

    @Test
    void servicePropertiesWinOverTheDefaults() {
        MockEnvironment environment = process(new MockEnvironment()
            .withProperty("management.tracing.sampling.probability", "0.2")
            .withProperty("management.otlp.metrics.export.enabled", "true"));

        assertThat(environment.getProperty("management.tracing.sampling.probability")).isEqualTo("0.2");
        assertThat(environment.getProperty("management.otlp.metrics.export.enabled")).isEqualTo("true");
    }
}
