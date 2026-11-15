package com.shopapp.payment.viewmodel.external;

import java.math.BigDecimal;

/** What PayPal captured, as payment-paypal relays it; verified before the order is marked paid (5-13). */
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
