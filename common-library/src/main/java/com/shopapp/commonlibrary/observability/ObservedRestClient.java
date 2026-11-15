package com.shopapp.commonlibrary.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.web.client.RestClient;

/**
 * Drop-in for {@code RestClient.builder()} in services that build their RestClients in constructors or static
 * helpers: the client is observed, so each call gets a client span and the {@code traceparent} header (decisions.md
 * 6-4). The observation registry is resolved on every call, so a client built before the registry bean exists (or
 * with tracing off, in unit tests) works and is simply not observed.
 */
public final class ObservedRestClient {

    private ObservedRestClient() {
    }

    public static RestClient.Builder builder() {
        return RestClient.builder().observationRegistry(ApplicationObservationRegistry.INSTANCE);
    }

    /** Forwards to the application's {@link ObservationRegistry}, or the no-op one before/without it. */
    static final class ApplicationObservationRegistry implements ObservationRegistry {

        static final ApplicationObservationRegistry INSTANCE = new ApplicationObservationRegistry();

        private volatile ObservationRegistry delegate = ObservationRegistry.NOOP;

        private ApplicationObservationRegistry() {
        }

        void bind(ObservationRegistry registry) {
            delegate = registry;
        }

        void unbind(ObservationRegistry registry) {
            if (delegate == registry) {
                delegate = ObservationRegistry.NOOP;
            }
        }

        @Override
        public Observation getCurrentObservation() {
            return delegate.getCurrentObservation();
        }

        @Override
        public Observation.Scope getCurrentObservationScope() {
            return delegate.getCurrentObservationScope();
        }

        @Override
        public void setCurrentObservationScope(Observation.Scope current) {
            delegate.setCurrentObservationScope(current);
        }

        @Override
        public ObservationConfig observationConfig() {
            return delegate.observationConfig();
        }

        @Override
        public boolean isNoop() {
            return delegate.isNoop();
        }
    }
}
