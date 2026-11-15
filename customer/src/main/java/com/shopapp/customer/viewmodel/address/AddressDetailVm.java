package com.shopapp.customer.viewmodel.address;

/** An address with its district, state and country names (location {@code AddressDetailVm}). */
public record AddressDetailVm(Long id, String contactName, String phone,
                              String addressLine1, String addressLine2, String city, String zipCode,
                              Long districtId, String districtName, Long stateOrProvinceId,
                              String stateOrProvinceName, Long countryId, String countryName) {
}
