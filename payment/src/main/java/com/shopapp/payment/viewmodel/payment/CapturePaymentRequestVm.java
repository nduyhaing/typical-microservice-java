package com.shopapp.payment.viewmodel.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * PayPal's return: the {@code token} query parameter PayPal appended to the return URL (the PayPal order id), for
 * {@code POST /capture} and {@code POST /cancel} (5-68). YAS also took {@code paymentMethod}; capture is PayPal only
 * here (COD is confirmed by an admin, decisions.md 5-15).
 */
public record CapturePaymentRequestVm(@NotBlank @Pattern(regexp = "[A-Z0-9]{1,64}") String token) {
}
