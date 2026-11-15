package com.shopapp.payment.paypal.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopapp.payment.paypal.config.PaypalProperties;
import com.shopapp.payment.paypal.exception.PaypalException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * PayPal's OAuth 2 access token (client-credentials grant with the REST app's client id and secret), cached until a
 * minute before it expires (PayPal answers ~9 h) (decisions.md 5-21). YAS built a new PayPal SDK client, and so a
 * new token, for every call.
 */
@Slf4j
@Service
public class PaypalTokenProvider {

    private static final long MARGIN_SECONDS = 60;

    private final PaypalProperties properties;
    private final RestClient restClient;
    private final Clock clock = Clock.systemUTC();
    private String token;
    private Instant renewAt = Instant.MIN;

    public PaypalTokenProvider(PaypalProperties properties) {
        this.properties = properties;
        this.restClient = RestClients.withTimeouts(properties.baseUrl());
    }

    /**
     * The cached token, or a new one.
     *
     * @throws PaypalException 503 when PayPal is not configured, 502 when PayPal cannot be reached or refuses
     */
    public synchronized String token() {
        if (!properties.configured()) {
            throw PaypalException.notConfigured();
        }
        Instant now = clock.instant();
        if (token != null && now.isBefore(renewAt)) {
            return token;
        }
        TokenResponse response;
        try {
            response = restClient.post()
                .uri("/v1/oauth2/token")
                .headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret(),
                    StandardCharsets.UTF_8))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials")
                .retrieve()
                .body(TokenResponse.class);
        } catch (RestClientException ex) {
            log.error("Could not get a PayPal access token: {}", ex.getMessage());
            throw PaypalException.unavailable();
        }
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            log.error("PayPal answered no access token");
            throw PaypalException.unavailable();
        }
        token = response.accessToken();
        long lifetime = response.expiresIn() == null ? 300 : response.expiresIn();
        renewAt = now.plusSeconds(Math.max(0, lifetime - MARGIN_SECONDS));
        return token;
    }

    /** Forgets the cached token (PayPal answered 401). */
    public synchronized void evict() {
        token = null;
        renewAt = Instant.MIN;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenResponse(@JsonProperty("access_token") String accessToken,
                         @JsonProperty("expires_in") Long expiresIn) {
    }
}
