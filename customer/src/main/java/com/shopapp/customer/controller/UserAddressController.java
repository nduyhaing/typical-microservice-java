package com.shopapp.customer.controller;

import com.shopapp.customer.service.UserAddressService;
import com.shopapp.customer.viewmodel.address.ActiveAddressVm;
import com.shopapp.customer.viewmodel.address.AddressDetailVm;
import com.shopapp.customer.viewmodel.address.AddressPostVm;
import com.shopapp.customer.viewmodel.useraddress.UserAddressVm;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in customer's address book (YAS paths); {@code {id}} is the location address id. */
@RestController
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService userAddressService;

    @GetMapping("/storefront/user-address")
    public ResponseEntity<List<ActiveAddressVm>> getUserAddresses() {
        return ResponseEntity.ok(userAddressService.getUserAddressList());
    }

    @GetMapping("/storefront/user-address/default-address")
    public ResponseEntity<AddressDetailVm> getDefaultAddress() {
        return ResponseEntity.ok(userAddressService.getAddressDefault());
    }

    @PostMapping("/storefront/user-address")
    public ResponseEntity<UserAddressVm> createAddress(@Valid @RequestBody AddressPostVm addressPostVm) {
        return ResponseEntity.ok(userAddressService.createAddress(addressPostVm));
    }

    @DeleteMapping("/storefront/user-address/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        userAddressService.deleteAddress(id);
        return ResponseEntity.ok().build();
    }

    /** Makes the address the default one (YAS "choose default"). */
    @PutMapping("/storefront/user-address/{id}")
    public ResponseEntity<Void> chooseDefaultAddress(@PathVariable Long id) {
        userAddressService.chooseDefaultAddress(id);
        return ResponseEntity.ok().build();
    }
}
