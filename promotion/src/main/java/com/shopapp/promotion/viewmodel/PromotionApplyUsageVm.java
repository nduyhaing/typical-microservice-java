package com.shopapp.promotion.viewmodel;

import com.shopapp.promotion.utils.Constants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Take one use of a coupon for one order: the discounted product ids and, when already known, the order id. */
public record PromotionApplyUsageVm(
    @NotBlank @Size(max = 100) String couponCode,
    @Size(max = Constants.MAX_IDS) List<@NotNull @Positive Long> productIds,
    @Positive Long orderId
) {
}
