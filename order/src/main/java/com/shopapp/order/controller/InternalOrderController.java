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
 * Service-to-service endpoints for the payment service (decisions.md 5-6), refused by nginx and both BFFs; per
 * capability realm roles of a client-credentials token (5-66): reading needs {@code SERVICE_ORDER_READ}, the payment
 * calls {@code SERVICE_ORDER_PAYMENT} (only the payment service has them). Replaces YAS
 * {@code PUT /storefront/orders/status}, which let any caller mark any order paid.
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

    /**
     * The payment service is about to capture money for the order (decisions.md 5-62): blocks cancelling for a short
     * time; 400 when the order is already paid or ended.
     */
    @PutMapping("/internal/orders/{id}/payment-in-progress")
    public ResponseEntity<OrderPaymentInfoVm> markPaymentInProgress(@PathVariable Long id) {
        return ResponseEntity.ok(lifecycleService.markPaymentInProgress(id));
    }

    /** A completed payment: the order becomes PAID (amount checked; idempotent per payment id). */
    @PutMapping("/internal/orders/{id}/payment")
    public ResponseEntity<OrderPaymentInfoVm> markPaid(@PathVariable Long id,
                                                       @Valid @RequestBody OrderPaymentPutVm payment) {
        return ResponseEntity.ok(lifecycleService.markPaid(id, payment));
    }
}
