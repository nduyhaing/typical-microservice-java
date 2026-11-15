package com.shopapp.inventory.viewmodel.warehouse;

import com.shopapp.inventory.viewmodel.address.AddressPostVm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Warehouse create/update body (YAS shape without the unused {@code id}). The address follows the rules of location
 * (decisions.md 3c-6): contact name, phone, first line and country required; state and district optional.
 */
public record WarehousePostVm(@NotBlank @Size(max = 450) String name,
                              @NotBlank @Size(max = 450) String contactName,
                              @NotBlank @Size(max = 25) String phone,
                              @NotBlank @Size(max = 450) String addressLine1,
                              @Size(max = 450) String addressLine2,
                              @Size(max = 450) String city,
                              @Size(max = 25) String zipCode,
                              Long districtId,
                              Long stateOrProvinceId,
                              @NotNull Long countryId) {

    public AddressPostVm toAddress() {
        return new AddressPostVm(contactName, phone, addressLine1, addressLine2, city, zipCode, districtId,
            stateOrProvinceId, countryId);
    }
}
