package com.shopapp.payment.service;

import com.shopapp.payment.config.PaymentServiceUrls;
import com.shopapp.payment.exception.PaymentException;
import com.shopapp.payment.utils.Constants;
import com.shopapp.payment.viewmodel.external.PaypalCaptureVm;
import com.shopapp.payment.viewmodel.external.PaypalOrderPostVm;
import com.shopapp.payment.viewmodel.external.PaypalOrderVm;
import com.shopapp.payment.viewmodel.external.PaypalStatusVm;
import com.shopapp.payment.viewmodel.external.RemoteErrorVm;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * payment-paypal's internal API (decisions.md 5-20..5-24) with the payment service's own token. Whether PayPal is
 * configured is asked from payment-paypal and remembered for 30 s (5-16): the PayPal provider is offered only then.
 */
@Slf4j
@Service
public class PaypalClient {

    private static final Duration STATUS_TTL = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final ServiceTokenProvider tokens;
    private final Clock clock = Clock.systemUTC();
    private Boolean configured;
    private Instant statusUntil = Instant.MIN;

    public PaypalClient(PaymentServiceUrls urls, ServiceTokenProvider tokens) {
        this.restClient = RestClients.withTimeouts(urls.paymentPaypal(), Duration.ofSeconds(20));
        this.tokens = tokens;
    }

    /** Whether payment-paypal has PayPal credentials; false when it is down (cached 30 s). */
    public synchronized boolean configured() {
        Instant now = clock.instant();
        if (configured != null && now.isBefore(statusUntil)) {
            return configured;
        }
        try {
            PaypalStatusVm status = ServiceCalls.send(tokens, token -> restClient.get()
                .uri("/internal/status")
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(PaypalStatusVm.class));
            configured = status != null && status.configured();
        } catch (RestClientException | ServiceTokenProvider.ServiceTokenException ex) {
            log.warn("Could not ask payment-paypal whether PayPal is configured: {}", ex.getMessage());
            configured = false;
        }
        statusUntil = now.plus(STATUS_TTL);
        return configured;
    }

    /** Forgets the remembered PayPal status (tests). */
    public synchronized void forgetStatus() {
        configured = null;
        statusUntil = Instant.MIN;
    }

    /**
     * Creates a PayPal order for the amount under our reference, idempotent per {@code requestId} (the payment's own
     * PayPal-Request-Id, 5-67), and answers the approval URL.
     */
    public PaypalOrderVm createOrder(BigDecimal amount, String reference, UUID requestId) {
        PaypalOrderVm order = call("create a PayPal order for " + reference, token -> restClient.post()
            .uri("/internal/orders")
            .headers(headers -> headers.setBearerAuth(token))
            .body(new PaypalOrderPostVm(amount, reference, requestId))
            .retrieve()
            .body(PaypalOrderVm.class));
        if (order == null || order.paypalOrderId() == null || order.approvalUrl() == null) {
            log.error("payment-paypal answered an unusable PayPal order: {}", order);
            throw PaymentException.unavailable(Constants.ErrorCode.PAYPAL_UNAVAILABLE);
        }
        return order;
    }

    /** Captures the approved PayPal order and answers what PayPal captured (not yet verified). */
    public PaypalCaptureVm capture(String paypalOrderId) {
        PaypalCaptureVm capture = call("capture PayPal order " + paypalOrderId, token -> restClient.post()
            .uri("/internal/orders/{id}/capture", paypalOrderId)
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve()
            .body(PaypalCaptureVm.class));
        if (capture == null) {
            throw PaymentException.unavailable(Constants.ErrorCode.PAYPAL_UNAVAILABLE);
        }
        return capture;
    }

    /**
     * payment-paypal's 400 (PayPal refused, title = PayPal's issue) and 404 become a 400 with that reason; 503 (not
     * configured) stays a 503 "PayPal is not configured"; anything else is a 503 "PayPal is unavailable".
     */
    private <T> T call(String what, java.util.function.Function<String, T> request) {
        try {
            return ServiceCalls.send(tokens, request);
        } catch (HttpClientErrorException ex) {
            RemoteErrorVm error = ServiceCalls.errorBody(ex);
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)
                || ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                log.warn("PayPal refused to {}: {}", what, error);
                String detail = error == null || error.detail() == null ? "PayPal refused the payment" : error.detail();
                throw new PaymentException(HttpStatus.BAD_REQUEST, error == null ? null : error.title(), detail);
            }
            log.error("payment-paypal refused to {} with {}", what, ex.getStatusCode());
            throw PaymentException.unavailable(Constants.ErrorCode.PAYPAL_UNAVAILABLE);
        } catch (HttpServerErrorException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.SERVICE_UNAVAILABLE)) {
                RemoteErrorVm error = ServiceCalls.errorBody(ex);
                if (error != null && "PayPal is not configured".equals(error.detail())) {
                    throw PaymentException.unavailable(Constants.ErrorCode.PAYPAL_NOT_CONFIGURED);
                }
            }
            log.error("Could not {}: payment-paypal answered {}", what, ex.getStatusCode());
            throw PaymentException.unavailable(Constants.ErrorCode.PAYPAL_UNAVAILABLE);
        } catch (RestClientException | ServiceTokenProvider.ServiceTokenException ex) {
            log.error("Could not {}: {}", what, ex.getMessage());
            throw PaymentException.unavailable(Constants.ErrorCode.PAYPAL_UNAVAILABLE);
        }
    }
}
