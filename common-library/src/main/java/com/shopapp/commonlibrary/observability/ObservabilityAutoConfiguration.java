package com.shopapp.commonlibrary.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationPredicate;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationContext;

/**
 * Shared telemetry wiring (decisions.md 6-1..6-4): binds {@link ObservedRestClient} to the application's observation
 * registry and, only when OTLP log export is configured, ships log records to the collector through the
 * OpenTelemetry logback appender (Spring Boot exports OTLP logs but does not bridge logback itself).
 *
 * <p>The services component-scan {@code com.shopapp.commonlibrary}, so this class can also be processed as a plain
 * configuration class before the auto-configurations: no {@code @ConditionalOnBean} here, the beans it needs are
 * looked up lazily ({@link ObjectProvider}).
 */
@AutoConfiguration(afterName = {
    "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
    "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration",
    "org.springframework.boot.opentelemetry.autoconfigure.logging.OpenTelemetryLoggingAutoConfiguration"
})
public class ObservabilityAutoConfiguration {

    /**
     * Outgoing calls (RestClient): the span's {@code http.url} keeps scheme, host and path but not the query string,
     * which can carry search terms, emails or ids (decisions.md 6-64). The low-cardinality {@code uri} is a template.
     */
    static final ObservationFilter CLIENT_URL_WITHOUT_QUERY = context -> {
        if (context instanceof ClientRequestObservationContext) {
            KeyValue url = context.getHighCardinalityKeyValue("http.url");
            if (url != null && url.getValue().indexOf('?') >= 0) {
                context.addHighCardinalityKeyValue(
                    KeyValue.of(url.getKey(), url.getValue().substring(0, url.getValue().indexOf('?'))));
            }
        }
        return context;
    };

    @Bean
    @ConditionalOnClass(name = "org.springframework.http.client.observation.ClientRequestObservationContext")
    ObservationFilter clientUrlWithoutQueryObservationFilter() {
        return CLIENT_URL_WITHOUT_QUERY;
    }

    @Bean
    ObservedRestClientBinding observedRestClientBinding(ObjectProvider<ObservationRegistry> registry) {
        return new ObservedRestClientBinding(registry);
    }

    /** Servlet services: no traces/metrics for actuator requests (health checks every 10 s per container). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    static class ServletActuatorFilter {

        static final ObservationPredicate NOT_ACTUATOR = (name, context) ->
            !(context instanceof ServerRequestObservationContext servlet)
                || !servlet.getCarrier().getRequestURI().contains("/actuator");

        /**
         * Spring Security's observations (filter chain, authentication, authorization) of a request whose
         * http.server.requests observation was dropped above have no (or a no-op) parent and would each become a
         * root span: dropped too (decisions.md 6-63). Servlet only: WebFlux's security decorator sets the parent
         * after the predicates ran, so there every security observation would look parentless.
         */
        static final ObservationPredicate NO_ORPHAN_SECURITY = (name, context) ->
            !name.startsWith("spring.security.")
                || context.getParentObservation() instanceof Observation parent && !parent.isNoop();

        @Bean
        ObservationPredicate servletActuatorObservationFilter() {
            return NOT_ACTUATOR;
        }

        @Bean
        ObservationPredicate servletOrphanSecurityObservationFilter() {
            return NO_ORPHAN_SECURITY;
        }
    }

    /** WebFlux (BFFs): the same for the BFFs' own actuator endpoints. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "reactor.core.publisher.Mono")
    static class ReactiveActuatorFilter {

        static final ObservationPredicate NOT_ACTUATOR = (name, context) ->
            !(context instanceof org.springframework.http.server.reactive.observation.ServerRequestObservationContext
                reactive) || !reactive.getCarrier().getPath().value().contains("/actuator");

        @Bean
        ObservationPredicate reactiveActuatorObservationFilter() {
            return NOT_ACTUATOR;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({OpenTelemetryAppender.class, LoggerContext.class})
    @ConditionalOnProperty("management.opentelemetry.logging.export.otlp.endpoint")
    static class OtlpLogbackConfiguration {

        @Bean
        OtlpLogbackAppender otlpLogbackAppender(ObjectProvider<OpenTelemetry> openTelemetry) {
            return new OtlpLogbackAppender(openTelemetry);
        }
    }

    static final class ObservedRestClientBinding implements InitializingBean, DisposableBean {

        private final ObjectProvider<ObservationRegistry> provider;
        private ObservationRegistry registry;

        ObservedRestClientBinding(ObjectProvider<ObservationRegistry> provider) {
            this.provider = provider;
        }

        @Override
        public void afterPropertiesSet() {
            registry = provider.getIfAvailable();
            if (registry != null) {
                ObservedRestClient.ApplicationObservationRegistry.INSTANCE.bind(registry);
            }
        }

        @Override
        public void destroy() {
            if (registry != null) {
                ObservedRestClient.ApplicationObservationRegistry.INSTANCE.unbind(registry);
            }
        }
    }

    /**
     * Attaches an {@link OpenTelemetryAppender} to the root logger. Records go to the SDK's batch processor (bounded
     * queue, background export), so a slow or unreachable collector never blocks the logging thread; records are
     * dropped when the queue is full. OpenTelemetry's own loggers are filtered out so export failures cannot feed
     * back into the export queue.
     */
    static final class OtlpLogbackAppender implements InitializingBean, DisposableBean {

        static final String APPENDER_NAME = "OTLP";

        private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
        private OpenTelemetryAppender appender;

        OtlpLogbackAppender(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
            this.openTelemetryProvider = openTelemetryProvider;
        }

        @Override
        public void afterPropertiesSet() {
            OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
            ILoggerFactory factory = LoggerFactory.getILoggerFactory();
            if (openTelemetry == null || !(factory instanceof LoggerContext loggerContext)) {
                return;
            }
            OpenTelemetryAppender otel = new OpenTelemetryAppender();
            otel.setName(APPENDER_NAME);
            otel.setContext(loggerContext);
            otel.setOpenTelemetry(openTelemetry);
            Filter<ILoggingEvent> notOpenTelemetry = new Filter<>() {
                @Override
                public FilterReply decide(ILoggingEvent event) {
                    return event.getLoggerName().startsWith("io.opentelemetry")
                        ? FilterReply.DENY
                        : FilterReply.NEUTRAL;
                }
            };
            notOpenTelemetry.start();
            otel.addFilter(notOpenTelemetry);
            otel.start();
            loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(otel);
            appender = otel;
        }

        @Override
        public void destroy() {
            if (appender != null && LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext) {
                loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
                appender.stop();
                appender = null;
            }
        }
    }
}
