package com.shopapp.payment.controller;

import com.shopapp.payment.service.PaymentService;
import com.shopapp.payment.viewmodel.payment.CapturePaymentRequestVm;
import com.shopapp.payment.viewmodel.payment.CapturePaymentResponseVm;
import com.shopapp.payment.viewmodel.payment.InitPaymentRequestVm;
import com.shopapp.payment.viewmodel.payment.InitPaymentResponseVm;
import com.shopapp.payment.viewmodel.payment.PaymentCancelVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** YAS payment endpoints (same paths), realm role CUSTOMER (decisions.md 5-12..5-15). */
@RestController
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/init")
    public InitPaymentResponseVm initPayment(@Valid @RequestBody InitPaymentRequestVm request) {
        return paymentService.initPayment(request);
    }

    @PostMapping("/capture")
    public CapturePaymentResponseVm capturePayment(@Valid @RequestBody CapturePaymentRequestVm request) {
        return paymentService.capturePayment(request.token());
    }

    /** PayPal's cancel return (YAS answered the text "Payment cancelled"; here the buyer's payment is cancelled). */
    @GetMapping("/cancel")
    public PaymentCancelVm cancelPayment(@RequestParam @Pattern(regexp = "[A-Z0-9]{1,64}") String token) {
        return paymentService.cancelPayment(token);
    }
}
