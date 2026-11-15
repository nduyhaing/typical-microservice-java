package com.shopapp.customer.viewmodel.useraddress;

import com.shopapp.customer.model.UserAddress;
import com.shopapp.customer.viewmodel.address.AddressVm;

/** A newly saved address: the link and the address location created (YAS). */
public record UserAddressVm(Long id, String userId, AddressVm addressGetVm, Boolean isActive) {

    public static UserAddressVm fromModel(UserAddress userAddress, AddressVm addressGetVm) {
        return new UserAddressVm(userAddress.getId(), userAddress.getUserId(), addressGetVm,
            userAddress.getIsActive());
    }
}
