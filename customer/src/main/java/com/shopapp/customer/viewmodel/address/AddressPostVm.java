package com.shopapp.customer.viewmodel.address;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * New saved address (YAS shape plus {@code addressLine2}), checked like location's body so bad input never reaches
 * location; location checks that the state belongs to the country and the district to the state.
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
