package com.shopapp.inventory.viewmodel.address;

/** Location {@code POST/PUT /storefront/addresses} body (YAS shape). */
public record AddressPostVm(String contactName, String phone, String addressLine1, String addressLine2, String city,
                            String zipCode, Long districtId, Long stateOrProvinceId, Long countryId) {
}
