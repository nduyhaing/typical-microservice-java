package com.shopapp.payment.controller;

import com.shopapp.payment.model.enumeration.PaymentMethod;
import com.shopapp.payment.model.enumeration.PaymentStatus;
import com.shopapp.payment.service.PaymentService;
import com.shopapp.payment.utils.Constants;
import com.shopapp.payment.viewmodel.payment.PaymentListVm;
import com.shopapp.payment.viewmodel.payment.PaymentVm;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin payments (shop_app additions; YAS had no payments list or COD confirmation; decisions.md 5-15, 5-17). */
@RestController
@RequiredArgsConstructor
public class PaymentBackofficeController {

    private final PaymentService paymentService;

    @GetMapping("/backoffice/payments")
    public PaymentListVm getPayments(
        @RequestParam(required = false) @Positive Long orderId,
        @RequestParam(required = false) PaymentStatus paymentStatus,
        @RequestParam(required = false) PaymentMethod paymentMethod,
        @RequestParam(defaultValue = "false") boolean needsAttention,
        @RequestParam(defaultValue = "0") @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(defaultValue = "10") @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        return paymentService.getPayments(orderId, paymentStatus, paymentMethod, needsAttention, pageNo, pageSize);
    }

    /** Cash collected on delivery: the order is marked paid with the recorded amount. */
    @PutMapping("/backoffice/payments/{id}/confirm")
    public PaymentVm confirmCod(@PathVariable @Positive Long id) {
        return paymentService.confirmCod(id);
    }
}
