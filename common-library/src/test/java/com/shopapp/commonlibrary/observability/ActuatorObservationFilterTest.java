package com.shopapp.commonlibrary.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Health checks every 10 s per container must not fill Tempo with traces (decisions.md 6-4). */
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
}
