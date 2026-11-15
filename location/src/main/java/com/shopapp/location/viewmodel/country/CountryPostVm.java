package com.shopapp.location.viewmodel.country;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Country create/update body (YAS shape; YAS's required {@code id} field is not needed and ignored if sent).
 */
public record CountryPostVm(@NotBlank @Size(max = 3) String code2,
                            @NotBlank @Size(max = 450) String name,
                            @Size(max = 3) String code3,
                            Boolean isBillingEnabled,
                            Boolean isShippingEnabled,
                            Boolean isCityEnabled,
                            Boolean isZipCodeEnabled,
                            Boolean isDistrictEnabled) {
}
