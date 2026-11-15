package com.shopapp.rating.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.observability.ObservedRestClient;
import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import java.time.Duration;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Shared RestClient setup (2 s connect, 5 s read, like inventory's) and error mapping for calls to order and product:
 * the caller maps 404; a 400 is passed on as 400 with the downstream detail; anything else (down, timeout, 401/403,
 * 5xx) is a 500 with a plain "service unavailable" message (decisions.md 3c-27, 4-35).
 */
@Slf4j
final class RemoteCalls {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private RemoteCalls() {
    }

    static RestClient restClient(String baseUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return ObservedRestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    /**
     * Runs the request; {@code onNotFound} (may be null) supplies the exception for a 404.
     */
    static <T> T call(String service, Supplier<T> request, Supplier<RuntimeException> onNotFound) {
        String unavailable = service + " service is unavailable, please try again later";
        try {
            return request.get();
        } catch (HttpClientErrorException ex) {
            if (onNotFound != null && ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw onNotFound.get();
            }
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                throw new BadRequestException(detail(ex));
            }
            log.error("{} refused the request: {}", service, ex.getMessage());
            throw new InternalServerErrorException(unavailable);
        } catch (RestClientException ex) {
            log.error("{} service unavailable: {}", service, ex.getMessage());
            throw new InternalServerErrorException(unavailable);
        }
    }

    private static String detail(HttpClientErrorException ex) {
        try {
            ErrorVm error = ex.getResponseBodyAs(ErrorVm.class);
            if (error != null && error.detail() != null) {
                return error.detail();
            }
        } catch (RuntimeException ignored) {
            // not an ErrorVm body
        }
        return "Request refused: " + ex.getStatusText();
    }
}
