package com.shopapp.inventory.viewmodel.warehouse;

import com.shopapp.inventory.model.Warehouse;
import com.shopapp.inventory.viewmodel.address.AddressDetailVm;

public record WarehouseDetailVm(Long id, String name, String contactName, String phone, String addressLine1,
                                String addressLine2, String city, String zipCode, Long districtId,
                                Long stateOrProvinceId, Long countryId) {

    public static WarehouseDetailVm of(Warehouse warehouse, AddressDetailVm address) {
        return new WarehouseDetailVm(warehouse.getId(), warehouse.getName(), address.contactName(), address.phone(),
            address.addressLine1(), address.addressLine2(), address.city(), address.zipCode(), address.districtId(),
            address.stateOrProvinceId(), address.countryId());
    }
}
