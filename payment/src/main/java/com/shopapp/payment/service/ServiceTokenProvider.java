package com.shopapp.payment.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopapp.payment.config.ServiceAuthProperties;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * An access token of the payment service itself (Keycloak client-credentials grant, client {@code payment-service}
 * with realm role {@code SERVICE}), cached until 30 s before it expires (decisions.md 5-11; copied from order's,
 * 5-5). Used for order's and payment-paypal's internal endpoints.
 */
@Slf4j
@Service
public class ServiceTokenProvider {

    /** Renew this long before the token expires, so a call never leaves with a token about to expire. */
    private static final long MARGIN_SECONDS = 30;

    private final ServiceAuthProperties properties;
    private final RestClient restClient;
    private final Clock clock;
    private String token;
    private Instant renewAt = Instant.MIN;

    public ServiceTokenProvider(ServiceAuthProperties properties) {
        this.properties = properties;
        this.restClient = RestClients.withTimeouts(properties.tokenUri());
        this.clock = Clock.systemUTC();
    }

    /**
     * The cached token, or a new one.
     *
     * @throws ServiceTokenException when Keycloak cannot be reached or refuses the client
     */
    public synchronized String token() {
        Instant now = clock.instant();
        if (token != null && now.isBefore(renewAt)) {
            return token;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        TokenResponse response;
        try {
            response = restClient.post()
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        } catch (RestClientException ex) {
            throw new ServiceTokenException("Could not get a service token: " + ex.getMessage());
        }
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw new ServiceTokenException("Keycloak answered no service token");
        }
        token = response.accessToken();
        long lifetime = response.expiresIn() == null ? 60 : response.expiresIn();
        renewAt = now.plusSeconds(Math.max(0, lifetime - MARGIN_SECONDS));
        return token;
    }

    /** Forgets the cached token (after a 401 from the called service). */
    public synchronized void evict() {
        token = null;
        renewAt = Instant.MIN;
    }

    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") Long expiresIn) {
    }

    /** No service token: the internal call is not made (answered 503). */
    public static class ServiceTokenException extends RuntimeException {

        public ServiceTokenException(String message) {
            super(message);
        }
    }
}
