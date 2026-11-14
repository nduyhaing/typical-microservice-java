package com.shopapp.order.controller;

import com.shopapp.order.service.CheckoutService;
import com.shopapp.order.viewmodel.checkout.CheckoutPostVm;
import com.shopapp.order.viewmodel.checkout.CheckoutVm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping("/storefront/checkouts")
    public ResponseEntity<CheckoutVm> createCheckout(@Valid @RequestBody CheckoutPostVm checkoutPostVm) {
        return ResponseEntity.ok(checkoutService.createCheckout(checkoutPostVm));
    }

    @GetMapping("/storefront/checkouts/{id}")
    public ResponseEntity<CheckoutVm> getPendingCheckout(@PathVariable String id) {
        return ResponseEntity.ok(checkoutService.getPendingCheckout(id));
    }
}
