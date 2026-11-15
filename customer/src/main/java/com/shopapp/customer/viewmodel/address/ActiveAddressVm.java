package com.shopapp.customer.viewmodel.address;

/** A saved address in the address book; {@code id} is the location address id, {@code isActive} the default. */
public record ActiveAddressVm(
    Long id,
    String contactName,
    String phone,
    String addressLine1,
    String addressLine2,
    String city,
    String zipCode,
    Long districtId,
    String districtName,
    Long stateOrProvinceId,
    String stateOrProvinceName,
    Long countryId,
    String countryName,
    Boolean isActive
) {

    public static ActiveAddressVm of(AddressDetailVm address, boolean isActive) {
        return new ActiveAddressVm(address.id(), address.contactName(), address.phone(), address.addressLine1(),
            address.addressLine2(), address.city(), address.zipCode(), address.districtId(), address.districtName(),
            address.stateOrProvinceId(), address.stateOrProvinceName(), address.countryId(), address.countryName(),
            isActive);
    }
}
