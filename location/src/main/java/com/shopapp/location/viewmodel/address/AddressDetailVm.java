package com.shopapp.location.viewmodel.address;

import com.shopapp.location.model.Address;
import com.shopapp.location.model.District;
import com.shopapp.location.model.StateOrProvince;

public record AddressDetailVm(Long id, String contactName, String phone,
                              String addressLine1, String addressLine2, String city, String zipCode,
                              Long districtId, String districtName, Long stateOrProvinceId,
                              String stateOrProvinceName, Long countryId, String countryName) {

    public static AddressDetailVm fromModel(Address address) {
        District district = address.getDistrict();
        StateOrProvince state = address.getStateOrProvince();
        return new AddressDetailVm(address.getId(), address.getContactName(), address.getPhone(),
            address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getZipCode(),
            district == null ? null : district.getId(),
            district == null ? null : district.getName(),
            state == null ? null : state.getId(),
            state == null ? null : state.getName(),
            address.getCountry().getId(),
            address.getCountry().getName());
    }
}
