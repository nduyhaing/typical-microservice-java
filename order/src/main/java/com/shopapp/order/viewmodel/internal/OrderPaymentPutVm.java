package com.shopapp.order.viewmodel.internal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Internal (payment service): a completed payment of an order (decisions.md 5-6). {@code amount} must equal the
 * order's total exactly; the payment service takes the amount from order, never from the browser.
 */
public record OrderPaymentPutVm(
    @NotNull @Positive Long paymentId,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount
) {
}
