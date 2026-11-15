package com.shopapp.location.viewmodel.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Address create/update body (YAS shape). Contact name, phone, first address line and country are required;
 * state and district are optional but must belong to the country / state when given.
 */
public record AddressPostVm(@NotBlank @Size(max = 450) String contactName,
                            @NotBlank @Size(max = 25) String phone,
                            @NotBlank @Size(max = 450) String addressLine1,
                            @Size(max = 450) String addressLine2,
                            @Size(max = 450) String city,
                            @Size(max = 25) String zipCode,
                            Long districtId,
                            Long stateOrProvinceId,
                            @NotNull Long countryId) {
}
