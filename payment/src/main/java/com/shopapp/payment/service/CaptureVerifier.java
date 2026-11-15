package com.shopapp.payment.service;

import com.shopapp.payment.model.Payment;
import com.shopapp.payment.utils.Constants;
import com.shopapp.payment.viewmodel.external.PaypalCaptureVm;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Checks a PayPal capture before an order is marked paid (decisions.md 5-13; YAS trusted whatever the capture said
 * and took the checkout id from a static field): PayPal order and capture COMPLETED, a capture id, the PayPal order
 * this payment created, our reference as {@code custom_id}, USD, and an amount equal to the payment's amount and to
 * the order total read from the order service just now.
 */
final class CaptureVerifier {

    private static final String COMPLETED = "COMPLETED";

    private CaptureVerifier() {
    }

    /** Why the capture must not pay the order, or empty when it may. */
    static Optional<String> problem(PaypalCaptureVm capture, Payment payment, BigDecimal orderTotal) {
        if (!COMPLETED.equals(capture.status()) || !COMPLETED.equals(capture.captureStatus())) {
            return Optional.of("PayPal order " + capture.status() + ", capture " + capture.captureStatus());
        }
        if (capture.captureId() == null || capture.captureId().isBlank()) {
            return Optional.of("PayPal answered no capture id");
        }
        if (!Objects.equals(capture.paypalOrderId(), payment.getPaymentProviderCheckoutId())) {
            return Optional.of("captured PayPal order " + capture.paypalOrderId() + " is not "
                + payment.getPaymentProviderCheckoutId());
        }
        if (!Objects.equals(capture.customId(), payment.reference())) {
            return Optional.of("captured reference " + capture.customId() + " is not " + payment.reference());
        }
        if (!Constants.CURRENCY.equals(capture.currency())) {
            return Optional.of("captured currency " + capture.currency() + " is not " + Constants.CURRENCY);
        }
        if (capture.amount() == null || payment.getAmount() == null || orderTotal == null
            || capture.amount().compareTo(payment.getAmount()) != 0 || capture.amount().compareTo(orderTotal) != 0) {
            return Optional.of("captured amount " + capture.amount() + " " + capture.currency()
                + " is not the order total " + orderTotal);
        }
        return Optional.empty();
    }
}
