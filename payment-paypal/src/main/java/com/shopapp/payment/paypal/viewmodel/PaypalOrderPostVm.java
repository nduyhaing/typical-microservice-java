package com.shopapp.payment.paypal.viewmodel;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Internal (payment service): create a PayPal order for one of our payments (decisions.md 5-21). {@code amount} is
 * the order total the payment service read from the order service (USD, at most 2 decimals); {@code reference} is
 * our payment reference, sent as the purchase unit's {@code custom_id} and {@code reference_id} and checked again on
 * capture. {@code requestId} is the payment's own random PayPal request id (stored with the payment, decisions.md
 * 5-67), sent as {@code PayPal-Request-Id}: a retried create is idempotent at PayPal, and a reused payment id (e.g.
 * after a database reset) can never be answered with an older PayPal order.
 */
public record PaypalOrderPostVm(
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
    @NotBlank @Size(max = 127) @Pattern(regexp = "[A-Za-z0-9_-]+") String reference,
    @NotNull UUID requestId
) {
}
