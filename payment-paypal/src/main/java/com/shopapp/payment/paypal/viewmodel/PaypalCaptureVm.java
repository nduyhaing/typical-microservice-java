package com.shopapp.payment.paypal.viewmodel;

import java.math.BigDecimal;

/**
 * A captured PayPal order, as PayPal answered it (decisions.md 5-21): the payment service verifies {@code status},
 * {@code captureStatus}, {@code amount}, {@code currency} and {@code customId} against its own payment and the order
 * before the order is marked paid. {@code captureId} is the gateway transaction id.
 */
public record PaypalCaptureVm(
    String paypalOrderId,
    String status,
    String captureId,
    String captureStatus,
    BigDecimal amount,
    String currency,
    String customId,
    BigDecimal paymentFee
) {
}
