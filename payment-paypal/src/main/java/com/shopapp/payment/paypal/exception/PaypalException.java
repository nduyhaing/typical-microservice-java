package com.shopapp.payment.paypal.exception;

import com.shopapp.commonlibrary.utils.MessagesUtils;
import org.springframework.http.HttpStatus;

/**
 * A PayPal call that did not succeed, with the status payment-paypal answers (decisions.md 5-24): 503 not
 * configured, 502 PayPal down / unreadable, 400 refused by PayPal (title = PayPal's issue code), 404 unknown order.
 */
public class PaypalException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    private PaypalException(HttpStatus status, String title, String messageCode, Object... args) {
        super(MessagesUtils.getMessage(messageCode, args));
        this.status = status;
        this.title = title;
    }

    public static PaypalException notConfigured() {
        return new PaypalException(HttpStatus.SERVICE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
            "PAYPAL_NOT_CONFIGURED");
    }

    public static PaypalException unavailable() {
        return new PaypalException(HttpStatus.BAD_GATEWAY, HttpStatus.BAD_GATEWAY.getReasonPhrase(),
            "PAYPAL_UNAVAILABLE");
    }

    public static PaypalException refused(String issue) {
        return new PaypalException(HttpStatus.BAD_REQUEST, issue, "PAYPAL_REFUSED", issue);
    }

    public static PaypalException orderNotFound(String paypalOrderId) {
        return new PaypalException(HttpStatus.NOT_FOUND, HttpStatus.NOT_FOUND.getReasonPhrase(),
            "PAYPAL_ORDER_NOT_FOUND", paypalOrderId);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }
}
