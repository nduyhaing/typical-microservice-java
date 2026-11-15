package com.shopapp.payment.controller;

import com.shopapp.payment.service.PaymentProviderService;
import com.shopapp.payment.viewmodel.provider.PaymentProviderAdminVm;
import com.shopapp.payment.viewmodel.provider.PaymentProviderReqVm;
import com.shopapp.payment.viewmodel.provider.PaymentProviderVm;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** YAS payment provider endpoints (same paths) plus the admin list (decisions.md 5-16, 5-17). */
@RestController
@RequiredArgsConstructor
public class PaymentProviderController {

    private final PaymentProviderService providerService;

    /** Public: the providers a customer can pay with right now. */
    @GetMapping("/storefront/payment-providers")
    public List<PaymentProviderVm> getAvailable() {
        return providerService.getAvailableProviders();
    }

    @GetMapping("/backoffice/payment-providers")
    public List<PaymentProviderAdminVm> getAll() {
        return providerService.getAllProviders();
    }

    @PostMapping("/backoffice/payment-providers")
    public ResponseEntity<PaymentProviderAdminVm> create(@Valid @RequestBody PaymentProviderReqVm request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(providerService.create(request));
    }

    @PutMapping("/backoffice/payment-providers")
    public PaymentProviderAdminVm update(@Valid @RequestBody PaymentProviderReqVm request) {
        return providerService.update(request);
    }
}
