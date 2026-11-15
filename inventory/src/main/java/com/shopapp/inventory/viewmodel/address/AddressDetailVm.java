package com.shopapp.inventory.viewmodel.address;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Location {@code GET /storefront/addresses/{id}} (YAS shape). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressDetailVm(Long id, String contactName, String phone, String addressLine1, String addressLine2,
                              String city, String zipCode, Long districtId, String districtName,
                              Long stateOrProvinceId, String stateOrProvinceName, Long countryId,
                              String countryName) {
}
