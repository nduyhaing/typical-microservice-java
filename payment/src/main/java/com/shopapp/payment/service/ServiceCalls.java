package com.shopapp.payment.service;

import com.shopapp.payment.viewmodel.external.RemoteErrorVm;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

/** Calls to internal endpoints with the payment service's own token (decisions.md 5-11). */
final class ServiceCalls {

    private ServiceCalls() {
    }

    /**
     * Sends the call with the service token; a 401 (token revoked or keys rotated) drops the cached token and tries
     * once more with a new one. Other failures are thrown to the caller.
     */
    static <T> T send(ServiceTokenProvider tokens, Function<String, T> call) {
        try {
            return call.apply(tokens.token());
        } catch (HttpClientErrorException ex) {
            if (!ex.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                throw ex;
            }
            tokens.evict();
            return call.apply(tokens.token());
        }
    }

    /** The other service's ErrorVm, or null when the body is not one. */
    static RemoteErrorVm errorBody(RestClientResponseException ex) {
        try {
            return ex.getResponseBodyAs(RemoteErrorVm.class);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}
