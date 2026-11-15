package com.shopapp.commonlibrary.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.ObservationFilterChainDecorator;

/** Health checks every 10 s per container must not fill Tempo with traces (decisions.md 6-4, 6-63). */
class ActuatorObservationFilterTest {

    private static ServerRequestObservationContext servlet(String uri) {
        return new ServerRequestObservationContext(new MockHttpServletRequest("GET", uri),
            new MockHttpServletResponse());
    }

    private static org.springframework.http.server.reactive.observation.ServerRequestObservationContext reactive(
        String uri) {
        return new org.springframework.http.server.reactive.observation.ServerRequestObservationContext(
            MockServerHttpRequest.get(uri).build(), new MockServerHttpResponse(), java.util.Map.of());
    }

    @Test
    void servletActuatorRequests_areNotObserved() {
        assertThat(ObservabilityAutoConfiguration.ServletActuatorFilter.NOT_ACTUATOR.test("http.server.requests",
            servlet("/tax/actuator/health"))).isFalse();
        assertThat(ObservabilityAutoConfiguration.ServletActuatorFilter.NOT_ACTUATOR.test("http.server.requests",
            servlet("/tax/backoffice/tax-classes"))).isTrue();
    }

    @Test
    void reactiveActuatorRequests_areNotObserved() {
        assertThat(ObservabilityAutoConfiguration.ReactiveActuatorFilter.NOT_ACTUATOR.test("http.server.requests",
            reactive("/actuator/health"))).isFalse();
        assertThat(ObservabilityAutoConfiguration.ReactiveActuatorFilter.NOT_ACTUATOR.test("http.server.requests",
            reactive("/api/order/storefront/orders"))).isTrue();
    }

    @Test
    void otherObservations_areKept() {
        Observation.Context other = new Observation.Context();
        assertThat(ObservabilityAutoConfiguration.ServletActuatorFilter.NOT_ACTUATOR.test("x", other)).isTrue();
        assertThat(ObservabilityAutoConfiguration.ReactiveActuatorFilter.NOT_ACTUATOR.test("x", other)).isTrue();
    }

    /**
     * The real Spring Security filter-chain decorator inside a (dropped) actuator request: without its parent
     * http.server.requests span each security observation would become a root span of its own.
     */
    @Test
    void securityObservationsOfAnActuatorRequest_areNotRecorded_butThoseOfOtherRequestsAre() throws Exception {
        List<String> stopped = new ArrayList<>();
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
            .observationPredicate(ObservabilityAutoConfiguration.ServletActuatorFilter.NOT_ACTUATOR)
            .observationPredicate(ObservabilityAutoConfiguration.ServletActuatorFilter.NO_ORPHAN_SECURITY)
            .observationHandler(new ObservationHandler<>() {
                @Override
                public void onStop(Observation.Context context) {
                    stopped.add(context.getName());
                }

                @Override
                public boolean supportsContext(Observation.Context context) {
                    return true;
                }
            });
        ObservationFilterChainDecorator decorator = new ObservationFilterChainDecorator(registry);

        for (String uri : List.of("/tax/actuator/health", "/tax/backoffice/tax-classes")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            Observation http = Observation.createNotStarted("http.server.requests",
                () -> new ServerRequestObservationContext(request, new MockHttpServletResponse()), registry);
            http.observeChecked(() -> decorator.decorate((req, res) -> { }, List.of((req, res, chain) ->
                chain.doFilter(req, res))).doFilter(request, new MockHttpServletResponse()));
            if (uri.contains("actuator")) {
                assertThat(stopped).as("nothing recorded for %s", uri).isEmpty();
            } else {
                assertThat(stopped).as("recorded for %s", uri)
                    .contains("http.server.requests").anyMatch(name -> name.startsWith("spring.security."));
            }
        }
    }

    @Test
    void parentlessSecurityObservations_areDropped_otherParentlessOnesAreKept() {
        Observation.Context orphan = new Observation.Context();
        assertThat(ObservabilityAutoConfiguration.ServletActuatorFilter.NO_ORPHAN_SECURITY
            .test("spring.security.filterchains", orphan)).isFalse();
        assertThat(ObservabilityAutoConfiguration.ServletActuatorFilter.NO_ORPHAN_SECURITY
            .test("http.server.requests", orphan)).isTrue();

        Observation.Context child = new Observation.Context();
        ObservationRegistry recording = ObservationRegistry.create();
        recording.observationConfig().observationHandler(context -> true);
        child.setParentObservation(Observation.start("parent", recording));
        assertThat(ObservabilityAutoConfiguration.ServletActuatorFilter.NO_ORPHAN_SECURITY
            .test("spring.security.filterchains", child)).isTrue();
    }
}
