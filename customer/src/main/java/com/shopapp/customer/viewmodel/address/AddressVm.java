package com.shopapp.customer.viewmodel.address;

/** An address as location returns it after create (location {@code AddressGetVm}). */
public record AddressVm(Long id, String contactName, String phone, String addressLine1, String addressLine2,
                        String city, String zipCode, Long districtId, Long stateOrProvinceId, Long countryId) {
}
