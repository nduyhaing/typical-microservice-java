package com.shopapp.payment.viewmodel.external;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * payment-paypal's {@code POST /internal/orders}: the order total (USD), our payment reference and the payment's own
 * PayPal request id (5-67).
 */
public record PaypalOrderPostVm(BigDecimal amount, String reference, UUID requestId) {
}
