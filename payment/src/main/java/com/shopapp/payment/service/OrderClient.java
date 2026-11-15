package com.shopapp.payment.service;

import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.payment.config.PaymentServiceUrls;
import com.shopapp.payment.exception.PaymentException;
import com.shopapp.payment.utils.Constants;
import com.shopapp.payment.viewmodel.external.OrderPaymentInfoVm;
import com.shopapp.payment.viewmodel.external.OrderPaymentPutVm;
import com.shopapp.payment.viewmodel.external.RemoteErrorVm;
import java.math.BigDecimal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Order's internal endpoints (decisions.md 5-6), called straight on the network with the payment service's own
 * token (5-11). Replaces YAS's {@code PUT /storefront/checkouts/status} and {@code PUT /storefront/orders/status},
 * which any caller could use to mark any order paid.
 */
@Slf4j
@Service
public class OrderClient {

    private final RestClient restClient;
    private final ServiceTokenProvider tokens;

    public OrderClient(PaymentServiceUrls urls, ServiceTokenProvider tokens) {
        this.restClient = RestClients.withTimeouts(urls.order());
        this.tokens = tokens;
    }

    /**
     * The order's owner, total and payment state.
     *
     * @throws NotFoundException when order does not know it
     * @throws PaymentException 503 when order is down or refuses the service token
     */
    public OrderPaymentInfoVm getOrder(Long orderId) {
        try {
            OrderPaymentInfoVm order = ServiceCalls.send(tokens, token -> restClient.get()
                .uri("/internal/orders/{id}", orderId)
                .headers(headers -> headers.setBearerAuth(token))
                .retrieve()
                .body(OrderPaymentInfoVm.class));
            if (order == null) {
                throw new RestClientException("empty answer");
            }
            return order;
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new NotFoundException(Constants.ErrorCode.ORDER_NOT_FOUND, orderId);
            }
            log.error("Order refused to answer order {}: {}", orderId, ex.getStatusCode());
            throw PaymentException.unavailable(Constants.ErrorCode.ORDER_SERVICE_UNAVAILABLE);
        } catch (RestClientException | ServiceTokenProvider.ServiceTokenException ex) {
            log.error("Could not read order {}: {}", orderId, ex.getMessage());
            throw PaymentException.unavailable(Constants.ErrorCode.ORDER_SERVICE_UNAVAILABLE);
        }
    }

    /**
     * Tells order the payment is complete (order checks the amount, idempotent per payment id).
     *
     * @throws PaymentException 400 with order's reason when order refuses, 503 when it is down
     */
    public OrderPaymentInfoVm markPaid(Long orderId, Long paymentId, BigDecimal amount) {
        try {
            return ServiceCalls.send(tokens, token -> restClient.put()
                .uri("/internal/orders/{id}/payment", orderId)
                .headers(headers -> headers.setBearerAuth(token))
                .body(new OrderPaymentPutVm(paymentId, amount))
                .retrieve()
                .body(OrderPaymentInfoVm.class));
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)
                || ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                RemoteErrorVm error = ServiceCalls.errorBody(ex);
                String detail = error == null || error.detail() == null
                    ? "The order service refused the payment" : error.detail();
                log.warn("Order refused payment {} of order {}: {}", paymentId, orderId, detail);
                throw new PaymentException(HttpStatus.BAD_REQUEST, null, detail);
            }
            log.error("Order refused payment {} of order {} with {}", paymentId, orderId, ex.getStatusCode());
            throw PaymentException.unavailable(Constants.ErrorCode.ORDER_SERVICE_UNAVAILABLE);
        } catch (RestClientException | ServiceTokenProvider.ServiceTokenException ex) {
            log.error("Could not mark order {} paid (payment {}): {}", orderId, paymentId, ex.getMessage());
            throw PaymentException.unavailable(Constants.ErrorCode.ORDER_SERVICE_UNAVAILABLE);
        }
    }
}
