package com.shopapp.payment.viewmodel.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Create / update a payment provider (YAS {@code CreatePaymentVm} / {@code UpdatePaymentVm} in one record; the id is
 * in the body on update, as in YAS). {@code additionalSettings} is display configuration, never credentials.
 */
public record PaymentProviderReqVm(
    @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,31}") String id,
    boolean enabled,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 255) String configureUrl,
    @Size(max = 255) String landingViewComponentName,
    @Size(max = 2000) String additionalSettings,
    @PositiveOrZero Long mediaId
) {
}
