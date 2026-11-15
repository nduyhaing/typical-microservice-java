package com.shopapp.promotion.viewmodel;

import java.util.List;

/** One page of promotions (YAS shape). */
public record PromotionListVm(
    List<PromotionDetailVm> promotionDetailVmList,
    int pageNo,
    int pageSize,
    long totalElements,
    int totalPages
) {
}
