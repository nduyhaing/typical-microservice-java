package com.shopapp.order.service;

import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/** Calls to other services' internal endpoints with the order service's own token (decisions.md 5-5). */
@Slf4j
final class InternalCalls {

    private InternalCalls() {
    }

    /**
     * Sends the call with the service token; a 401 (token revoked or keys rotated) drops the cached token and tries
     * once more with a new one. Never throws.
     *
     * @return whether the called service answered 2xx
     */
    static boolean send(ServiceTokenProvider tokens, String what, Consumer<String> call) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                call.accept(tokens.token());
                return true;
            } catch (HttpClientErrorException ex) {
                if (attempt == 1 && ex.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                    tokens.evict();
                    continue;
                }
                log.error("Could not {}: {}", what, ex.getMessage());
                return false;
            } catch (RestClientException | ServiceTokenProvider.ServiceTokenException ex) {
                log.error("Could not {}: {}", what, ex.getMessage());
                return false;
            }
        }
        return false;
    }
}
