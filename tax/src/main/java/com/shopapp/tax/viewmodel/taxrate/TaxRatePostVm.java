package com.shopapp.tax.viewmodel.taxrate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A rate in percent for a tax class at a location: country (required), optionally a state or province and a zip
 * code. Blank zip codes are stored as null (any zip).
 */
public record TaxRatePostVm(@NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 4)
                            BigDecimal rate,
                            @Size(max = 25) String zipCode,
                            @NotNull Long taxClassId,
                            Long stateOrProvinceId,
                            @NotNull Long countryId) {
}
