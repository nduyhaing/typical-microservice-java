package com.shopapp.location.viewmodel.address;

import com.shopapp.location.model.Address;
import com.shopapp.location.model.District;
import com.shopapp.location.model.StateOrProvince;

public record AddressGetVm(Long id, String contactName, String phone, String addressLine1, String addressLine2,
                           String city, String zipCode,
                           Long districtId, Long stateOrProvinceId, Long countryId) {

    public static AddressGetVm fromModel(Address address) {
        District district = address.getDistrict();
        StateOrProvince state = address.getStateOrProvince();
        return new AddressGetVm(address.getId(), address.getContactName(), address.getPhone(),
            address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getZipCode(),
            district == null ? null : district.getId(),
            state == null ? null : state.getId(),
            address.getCountry().getId());
    }
}
