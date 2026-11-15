package com.shopapp.order.viewmodel.external;

/** A saved address from location GET /storefront/addresses/{id} (location AddressDetailVm). */
public record LocationAddressVm(Long id, String contactName, String phone, String addressLine1, String addressLine2,
                                String city, String zipCode, Long districtId, String districtName,
                                Long stateOrProvinceId, String stateOrProvinceName, Long countryId,
                                String countryName) {
}
