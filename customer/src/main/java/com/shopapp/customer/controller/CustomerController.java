package com.shopapp.customer.controller;

import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.customer.service.CustomerService;
import com.shopapp.customer.viewmodel.CustomerVm;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/storefront/customer/profile")
    public ResponseEntity<CustomerVm> getCustomerProfile() {
        return ResponseEntity.ok(customerService.getCustomerProfile(AuthenticationUtils.extractUserId()));
    }
}
