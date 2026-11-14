package com.shopapp.order.viewmodel.order;

import com.shopapp.order.model.OrderAddress;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An address typed in at checkout. Location ids (country/state/district) come with the location service in slice 3
 * (decisions.md 2c-3).
 */
public record OrderAddressPostVm(
    @NotBlank @Size(max = 255) String contactName,
    @NotBlank @Size(max = 255) String phone,
    @NotBlank @Size(max = 255) String addressLine1,
    @Size(max = 255) String addressLine2,
    @NotBlank @Size(max = 255) String city,
    @Size(max = 255) String zipCode,
    @Size(max = 255) String districtName,
    @Size(max = 255) String stateOrProvinceName,
    @NotBlank @Size(max = 255) String countryName
) {

    public OrderAddress toModel() {
        return OrderAddress.builder()
            .contactName(contactName).phone(phone).addressLine1(addressLine1).addressLine2(addressLine2)
            .city(city).zipCode(zipCode).districtName(districtName).stateOrProvinceName(stateOrProvinceName)
            .countryName(countryName)
            .build();
    }
}
