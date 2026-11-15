package com.shopapp.order.controller;

import com.shopapp.order.model.enumeration.OrderStatus;
import com.shopapp.order.service.OrderLifecycleService;
import com.shopapp.order.utils.Constants;
import com.shopapp.order.viewmodel.order.OrderDetailVm;
import com.shopapp.order.viewmodel.order.OrderListVm;
import com.shopapp.order.viewmodel.order.OrderStatusPutVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Back-office orders (YAS /backoffice/orders, ADMIN; decisions.md 5-1). */
@Validated
@RestController
@RequiredArgsConstructor
public class OrderBackofficeController {

    private final OrderLifecycleService lifecycleService;

    /**
     * YAS list with filters: created between {@code createdFrom} and {@code createdTo} (ISO date-time), one of the
     * {@code orderStatus} values (comma-separated, empty: any), email and product name containing the text; newest
     * first, at most 100 per page. YAS's billing phone/country filters are left out (5-1).
     */
    @GetMapping("/backoffice/orders")
    public ResponseEntity<OrderListVm> getOrders(
        @RequestParam(value = "createdFrom", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime createdFrom,
        @RequestParam(value = "createdTo", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime createdTo,
        @RequestParam(value = "orderStatus", required = false) List<OrderStatus> orderStatus,
        @RequestParam(value = "email", defaultValue = "", required = false)
        @Size(max = Constants.MAX_FILTER_LENGTH) String email,
        @RequestParam(value = "productName", defaultValue = "", required = false)
        @Size(max = Constants.MAX_FILTER_LENGTH) String productName,
        @RequestParam(value = "pageNo", defaultValue = "0", required = false)
        @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "10", required = false)
        @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(lifecycleService.getOrders(createdFrom, createdTo, orderStatus, email, productName,
            pageNo, pageSize));
    }

    @GetMapping("/backoffice/orders/{id}")
    public ResponseEntity<OrderDetailVm> getOrder(@PathVariable Long id) {
        return ResponseEntity.ok(lifecycleService.getOrder(id));
    }

    /** Moves the order to another status (only the changes the status machine allows; else 400). */
    @PutMapping("/backoffice/orders/{id}/status")
    public ResponseEntity<OrderDetailVm> updateStatus(@PathVariable Long id,
                                                      @Valid @RequestBody OrderStatusPutVm request) {
        return ResponseEntity.ok(lifecycleService.updateStatus(id, request));
    }
}
