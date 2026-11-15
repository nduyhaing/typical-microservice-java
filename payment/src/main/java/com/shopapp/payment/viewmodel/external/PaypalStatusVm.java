package com.shopapp.payment.viewmodel.external;

/** payment-paypal's {@code GET /internal/status}. */
public record PaypalStatusVm(boolean configured, String currency) {
}
