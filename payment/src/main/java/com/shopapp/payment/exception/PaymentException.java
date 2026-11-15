package com.shopapp.payment.exception;

import com.shopapp.commonlibrary.utils.MessagesUtils;
import org.springframework.http.HttpStatus;

/**
 * A payment request that failed with a specific status: 503 when order or PayPal is down / not configured, 400 with
 * the order service's or PayPal's reason (title = its code) (decisions.md 5-14).
 */
public class PaymentException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    public PaymentException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title == null ? status.getReasonPhrase() : title;
    }

    public static PaymentException unavailable(String messageCode) {
        return new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, null, MessagesUtils.getMessage(messageCode));
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
