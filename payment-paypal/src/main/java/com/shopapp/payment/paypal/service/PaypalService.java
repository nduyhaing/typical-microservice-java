package com.shopapp.payment.paypal.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.shopapp.payment.paypal.config.PaypalProperties;
import com.shopapp.payment.paypal.exception.PaypalException;
import com.shopapp.payment.paypal.viewmodel.PaypalCaptureVm;
import com.shopapp.payment.paypal.viewmodel.PaypalOrderPostVm;
import com.shopapp.payment.paypal.viewmodel.PaypalOrderVm;
import com.shopapp.payment.paypal.viewmodel.PaypalStatusVm;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * PayPal Orders v2 over REST (decisions.md 5-21; YAS used the deprecated PayPal Checkout Java SDK): create an order
 * for the exact amount in USD with our payment reference as {@code custom_id} and the storefront return/cancel URLs,
 * then capture it after the buyer approved. Both calls send a {@code PayPal-Request-Id} (create: the payment's own
 * random id, 5-67), so a retried call is idempotent at PayPal. A capture PayPal answers ORDER_ALREADY_CAPTURED (an
 * earlier capture whose answer was lost) reads the PayPal order and answers its completed capture, which the payment
 * service verifies as usual. A 401 drops the cached token and retries once.
 */
@Slf4j
@Service
public class PaypalService {

    public static final String CURRENCY = "USD";
    private static final String ALREADY_CAPTURED = "ORDER_ALREADY_CAPTURED";
    private static final Set<String> APPROVAL_RELS = Set.of("payer-action", "approve");

    private final PaypalProperties properties;
    private final PaypalTokenProvider tokens;
    private final RestClient restClient;

    public PaypalService(PaypalProperties properties, PaypalTokenProvider tokens) {
        this.properties = properties;
        this.tokens = tokens;
        this.restClient = RestClients.withTimeouts(properties.baseUrl());
    }

    public PaypalStatusVm status() {
        return new PaypalStatusVm(properties.configured(), CURRENCY);
    }

    /** Creates a PayPal order (intent CAPTURE) and answers the approval URL. Never caps the amount (YAS did). */
    public PaypalOrderVm createOrder(PaypalOrderPostVm request) {
        Map<String, Object> body = Map.of(
            "intent", "CAPTURE",
            "purchase_units", List.of(Map.of(
                "reference_id", request.reference(),
                "custom_id", request.reference(),
                "amount", Map.of(
                    "currency_code", CURRENCY,
                    "value", request.amount().setScale(2, RoundingMode.UNNECESSARY).toPlainString()))),
            "payment_source", Map.of("paypal", Map.of("experience_context", Map.of(
                "return_url", properties.returnUrl(),
                "cancel_url", properties.cancelUrl(),
                "brand_name", properties.brandName(),
                "shipping_preference", "NO_SHIPPING",
                "user_action", "PAY_NOW"))));
        OrderResponse order = call("create a PayPal order for " + request.reference(), null, token ->
            restClient.post()
                .uri("/v2/checkout/orders")
                .headers(headers -> {
                    headers.setBearerAuth(token);
                    headers.set("PayPal-Request-Id", request.requestId().toString());
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(OrderResponse.class));
        String approvalUrl = order == null || order.links() == null ? null : order.links().stream()
            .filter(link -> APPROVAL_RELS.contains(link.rel()))
            .map(Link::href)
            .findFirst()
            .orElse(null);
        if (order == null || order.id() == null || approvalUrl == null) {
            log.error("PayPal answered an order without an id or approval link: {}", order);
            throw PaypalException.unavailable();
        }
        return new PaypalOrderVm(order.id(), order.status(), approvalUrl);
    }

    /** Captures an approved PayPal order and answers what PayPal captured (the caller verifies it). */
    public PaypalCaptureVm capture(String paypalOrderId) {
        OrderResponse order;
        try {
            order = call("capture PayPal order " + paypalOrderId, paypalOrderId, token ->
                restClient.post()
                    .uri("/v2/checkout/orders/{id}/capture", paypalOrderId)
                    .headers(headers -> {
                        headers.setBearerAuth(token);
                        headers.set("PayPal-Request-Id", "capture-" + paypalOrderId);
                    })
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{}")
                    .retrieve()
                    .body(OrderResponse.class));
        } catch (PaypalException ex) {
            if (!ALREADY_CAPTURED.equals(ex.getTitle())) {
                throw ex;
            }
            order = alreadyCaptured(paypalOrderId, ex);
        }
        Capture capture = firstCapture(order);
        if (order == null || capture == null) {
            log.error("PayPal answered a capture without a capture: {}", order);
            throw PaypalException.unavailable();
        }
        Money amount = capture.amount();
        BigDecimal fee = capture.breakdown() == null || capture.breakdown().paypalFee() == null ? null
            : decimal(capture.breakdown().paypalFee().value());
        return new PaypalCaptureVm(order.id(), order.status(), capture.id(), capture.status(),
            amount == null ? null : decimal(amount.value()), amount == null ? null : amount.currencyCode(),
            capture.customId(), fee);
    }

    /** Reads the PayPal order of an ORDER_ALREADY_CAPTURED answer: its completed capture, else the original 400. */
    private OrderResponse alreadyCaptured(String paypalOrderId, PaypalException refused) {
        log.warn("PayPal order {} was already captured; reading it to answer its capture", paypalOrderId);
        OrderResponse order = call("read PayPal order " + paypalOrderId, paypalOrderId, token -> restClient.get()
            .uri("/v2/checkout/orders/{id}", paypalOrderId)
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve()
            .body(OrderResponse.class));
        Capture capture = firstCapture(order);
        if (capture == null || !"COMPLETED".equals(capture.status())) {
            throw refused;
        }
        return order;
    }

    private static Capture firstCapture(OrderResponse order) {
        if (order == null || order.purchaseUnits() == null || order.purchaseUnits().isEmpty()) {
            return null;
        }
        PurchaseUnit unit = order.purchaseUnits().getFirst();
        if (unit.payments() == null || unit.payments().captures() == null || unit.payments().captures().isEmpty()) {
            return null;
        }
        return unit.payments().captures().getFirst();
    }

    private static BigDecimal decimal(String value) {
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * One PayPal call with the cached token; a 401 drops it and tries once more. PayPal's 400/422 become a 400 with
     * PayPal's issue code, 404 a 404, anything else (5xx, timeout, unreadable) a 502.
     */
    private <T> T call(String what, String paypalOrderId, Function<String, T> request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return request.apply(tokens.token());
            } catch (HttpClientErrorException ex) {
                if (ex.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED) && attempt == 1) {
                    tokens.evict();
                    continue;
                }
                if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND) && paypalOrderId != null) {
                    throw PaypalException.orderNotFound(paypalOrderId);
                }
                if (ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)
                    || ex.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_CONTENT)) {
                    throw PaypalException.refused(issueOf(ex));
                }
                log.error("Could not {}: PayPal answered {}", what, ex.getStatusCode());
                throw PaypalException.unavailable();
            } catch (RestClientException ex) {
                log.error("Could not {}: {}", what, ex.getMessage());
                throw PaypalException.unavailable();
            }
        }
    }

    /** PayPal's first {@code details[].issue} (e.g. ORDER_NOT_APPROVED), else its {@code name}. */
    private static String issueOf(HttpClientErrorException ex) {
        try {
            ErrorResponse error = ex.getResponseBodyAs(ErrorResponse.class);
            if (error != null && error.details() != null && !error.details().isEmpty()
                && validCode(error.details().getFirst().issue())) {
                return error.details().getFirst().issue();
            }
            if (error != null && validCode(error.name())) {
                return error.name();
            }
        } catch (RuntimeException unreadable) {
            log.debug("Unreadable PayPal error body: {}", unreadable.getMessage());
        }
        return "PAYPAL_REFUSED";
    }

    private static boolean validCode(String code) {
        return code != null && code.matches("[A-Z][A-Z0-9_]{1,63}");
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderResponse(String id, String status, List<Link> links,
                         @JsonProperty("purchase_units") List<PurchaseUnit> purchaseUnits) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Link(String href, String rel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PurchaseUnit(Payments payments) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payments(List<Capture> captures) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Capture(String id, String status, Money amount, @JsonProperty("custom_id") String customId,
                   @JsonProperty("seller_receivable_breakdown") Breakdown breakdown) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Breakdown(@JsonProperty("paypal_fee") Money paypalFee) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Money(@JsonProperty("currency_code") String currencyCode, String value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorResponse(String name, List<Detail> details) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Detail(String issue) {
    }
}
