package com.shopapp.order.viewmodel.order;

import java.util.List;

/** A page of the back-office order list (YAS OrderListVm). */
public record OrderListVm(List<OrderBriefVm> orderList, long totalElements, int totalPages) {
}
