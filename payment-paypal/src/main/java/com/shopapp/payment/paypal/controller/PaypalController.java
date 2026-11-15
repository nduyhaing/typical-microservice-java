package com.shopapp.payment.paypal.controller;

import com.shopapp.payment.paypal.service.PaypalService;
import com.shopapp.payment.paypal.viewmodel.PaypalCaptureVm;
import com.shopapp.payment.paypal.viewmodel.PaypalOrderPostVm;
import com.shopapp.payment.paypal.viewmodel.PaypalOrderVm;
import com.shopapp.payment.paypal.viewmodel.PaypalStatusVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal PayPal API for the payment service (realm role SERVICE; refused at nginx and both BFFs) (decisions.md
 * 5-20, 5-22). YAS payment-paypal was a library inside the payment service; here it is its own service so the PayPal
 * credentials live in one container only.
 */
@RestController
@RequiredArgsConstructor
public class PaypalController {

    private final PaypalService paypalService;

    @GetMapping("/internal/status")
    public PaypalStatusVm status() {
        return paypalService.status();
    }

    @PostMapping("/internal/orders")
    public PaypalOrderVm createOrder(@Valid @RequestBody PaypalOrderPostVm request) {
        return paypalService.createOrder(request);
    }

    @PostMapping("/internal/orders/{paypalOrderId}/capture")
    public PaypalCaptureVm capture(@PathVariable @Pattern(regexp = "[A-Z0-9]{1,64}") String paypalOrderId) {
        return paypalService.capture(paypalOrderId);
    }
}
