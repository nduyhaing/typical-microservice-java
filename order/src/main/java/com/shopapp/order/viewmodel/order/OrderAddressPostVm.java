package com.shopapp.order.viewmodel.order;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A new address typed in at checkout. The country is chosen from location's list (required); state/province and
 * district are optional but must belong to the country / state. Names come from location, never from the browser
 * (decisions.md 3c-41).
 */
public record OrderAddressPostVm(
    @NotBlank @Size(max = 255) String contactName,
    @NotBlank @Size(max = 255) String phone,
    @NotBlank @Size(max = 255) String addressLine1,
    @Size(max = 255) String addressLine2,
    @Size(max = 255) String city,
    @Size(max = 25) String zipCode,
    @NotNull Long countryId,
    Long stateOrProvinceId,
    Long districtId
) {
}
