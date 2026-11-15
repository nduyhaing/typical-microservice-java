package com.shopapp.payment.viewmodel.external;

import java.math.BigDecimal;

/** payment-paypal's {@code POST /internal/orders}: the order total (USD) and our payment reference. */
public record PaypalOrderPostVm(BigDecimal amount, String reference) {
}
