package com.shopapp.location.controller;

import com.shopapp.location.service.AddressService;
import com.shopapp.location.utils.Constants;
import com.shopapp.location.viewmodel.address.AddressDetailVm;
import com.shopapp.location.viewmodel.address.AddressGetVm;
import com.shopapp.location.viewmodel.address.AddressPostVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Saved addresses of the signed-in user (called by the customer service with the user's token).
 */
@RestController
@RequestMapping(Constants.ApiConstant.ADDRESSES_URL)
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public ResponseEntity<AddressGetVm> createAddress(@Valid @RequestBody AddressPostVm dto) {
        return ResponseEntity.ok(addressService.createAddress(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateAddress(@PathVariable Long id, @Valid @RequestBody AddressPostVm dto) {
        addressService.updateAddress(id, dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AddressDetailVm> getAddressById(@PathVariable Long id) {
        return ResponseEntity.ok(addressService.getAddress(id));
    }

    /** The caller's addresses among up to 100 ids (others' and unknown ids are skipped). */
    @GetMapping
    public ResponseEntity<List<AddressDetailVm>> getAddressList(
        @RequestParam @Size(max = Constants.Limits.MAX_IDS) List<Long> ids) {
        return ResponseEntity.ok(addressService.getAddressList(ids));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAddress(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return ResponseEntity.ok().build();
    }
}
