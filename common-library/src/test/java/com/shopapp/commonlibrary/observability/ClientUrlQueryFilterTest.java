package com.shopapp.commonlibrary.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.mock.http.client.MockClientHttpRequest;

/** Outgoing call spans keep the URL without its query string (search terms, ids, emails) (decisions.md 6-64). */
class ClientUrlQueryFilterTest {

    private static ClientRequestObservationContext context(String url) {
        ClientRequestObservationContext context = new ClientRequestObservationContext(
            new MockClientHttpRequest(HttpMethod.GET, URI.create(url)));
        context.addHighCardinalityKeyValue(KeyValue.of("http.url", url));
        context.addLowCardinalityKeyValue(KeyValue.of("uri", "/customer/backoffice/customers"));
        return context;
    }

    private static String url(Observation.Context context) {
        return context.getHighCardinalityKeyValue("http.url").getValue();
    }

    @Test
    void queryStringIsDropped() {
        Observation.Context mapped = ObservabilityAutoConfiguration.CLIENT_URL_WITHOUT_QUERY.map(
            context("http://customer:8080/customer/backoffice/customers?email=a%40b.c&page=0"));

        assertThat(url(mapped)).isEqualTo("http://customer:8080/customer/backoffice/customers");
        assertThat(mapped.getLowCardinalityKeyValue("uri").getValue()).isEqualTo("/customer/backoffice/customers");
    }

    @Test
    void urlWithoutQuery_andOtherObservations_areUnchanged() {
        assertThat(url(ObservabilityAutoConfiguration.CLIENT_URL_WITHOUT_QUERY.map(
            context("http://tax:8080/tax/backoffice/tax-classes")))).isEqualTo(
                "http://tax:8080/tax/backoffice/tax-classes");

        Observation.Context other = new Observation.Context();
        other.addHighCardinalityKeyValue(KeyValue.of("http.url", "http://x/?q=1"));
        assertThat(url(ObservabilityAutoConfiguration.CLIENT_URL_WITHOUT_QUERY.map(other))).isEqualTo("http://x/?q=1");
    }
}
