package com.shopapp.payment.viewmodel.external;

import java.math.BigDecimal;

/** Order's internal {@code PUT /internal/orders/{id}/payment}: amount must equal the order total (5-6). */
public record OrderPaymentPutVm(Long paymentId, BigDecimal amount) {
}
