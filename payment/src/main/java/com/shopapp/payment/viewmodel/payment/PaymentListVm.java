package com.shopapp.payment.viewmodel.payment;

import java.util.List;

public record PaymentListVm(
    List<PaymentVm> payments,
    int pageNo,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean isLast
) {
}
