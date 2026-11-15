package com.shopapp.order.controller;

import com.shopapp.order.service.OrderLifecycleService;
import com.shopapp.order.service.OrderService;
import com.shopapp.order.viewmodel.order.OrderExistsByProductAndUserGetVm;
import com.shopapp.order.viewmodel.order.OrderPostVm;
import com.shopapp.order.viewmodel.order.OrderPreviewPostVm;
import com.shopapp.order.viewmodel.order.OrderPreviewVm;
import com.shopapp.order.viewmodel.order.OrderVm;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final OrderLifecycleService lifecycleService;

    @PostMapping("/storefront/orders")
    public ResponseEntity<OrderVm> createOrder(@Valid @RequestBody OrderPostVm orderPostVm) {
        return ResponseEntity.ok(orderService.createOrder(orderPostVm));
    }

    /** Subtotal, tax per line and total of a checkout shipped to an address, priced by the server (3c-46). */
    @PostMapping("/storefront/orders/preview")
    public ResponseEntity<OrderPreviewVm> previewOrder(@Valid @RequestBody OrderPreviewPostVm previewPostVm) {
        return ResponseEntity.ok(orderService.previewOrder(previewPostVm));
    }

    /** YAS: did the caller buy the product (or a variation of it)? The rating service asks before a review (4-30). */
    @GetMapping("/storefront/orders/completed")
    public ResponseEntity<OrderExistsByProductAndUserGetVm> checkOrderExistsByProductIdAndUserIdWithStatus(
        @RequestParam Long productId) {
        return ResponseEntity.ok(orderService.hasBoughtProduct(productId));
    }

    @GetMapping("/storefront/orders/my-orders")
    public ResponseEntity<List<OrderVm>> getMyOrders() {
        return ResponseEntity.ok(orderService.getMyOrders());
    }

    /**
     * The customer cancels their own order while it is PENDING or PENDING_PAYMENT; its stock and coupon use are given
     * back. Someone else's order is a 404; repeating the call is safe (5-1, 5-4).
     */
    @PostMapping("/storefront/orders/{id}/cancel")
    public ResponseEntity<OrderVm> cancelOrder(@PathVariable Long id) {
        return ResponseEntity.ok(lifecycleService.cancelOwnOrder(id));
    }

    @GetMapping("/storefront/orders/checkout/{id}")
    public ResponseEntity<OrderVm> getOrderByCheckoutId(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getOrderByCheckoutId(id));
    }
}
