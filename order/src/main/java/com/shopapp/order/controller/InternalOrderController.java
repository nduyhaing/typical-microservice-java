package com.shopapp.order.controller;

import com.shopapp.order.service.OrderLifecycleService;
import com.shopapp.order.viewmodel.internal.OrderPaymentInfoVm;
import com.shopapp.order.viewmodel.internal.OrderPaymentPutVm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service endpoints for the payment service (decisions.md 5-6): realm role SERVICE only (a
 * client-credentials token), refused by nginx and both BFFs. Replaces YAS {@code PUT /storefront/orders/status}, which
 * let any caller mark any order paid.
 */
@RestController
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderLifecycleService lifecycleService;

    /** The order's owner, amount and payment state: payment charges this amount, never one from the browser. */
    @GetMapping("/internal/orders/{id}")
    public ResponseEntity<OrderPaymentInfoVm> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(lifecycleService.getPaymentInfo(id));
    }

    /** A completed payment: the order becomes PAID (amount checked; idempotent per payment id). */
    @PutMapping("/internal/orders/{id}/payment")
    public ResponseEntity<OrderPaymentInfoVm> markPaid(@PathVariable Long id,
                                                       @Valid @RequestBody OrderPaymentPutVm payment) {
        return ResponseEntity.ok(lifecycleService.markPaid(id, payment));
    }
}
