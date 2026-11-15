package com.shopapp.promotion.viewmodel;

import com.shopapp.promotion.utils.Constants;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** YAS PromotionVerifyVm with the order price as a decimal (YAS: Long) and bounded product ids. */
public record PromotionVerifyVm(
    @NotBlank @Size(max = 100) String couponCode,
    @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal orderPrice,
    @NotEmpty @Size(max = Constants.MAX_IDS) List<@NotNull @Positive Long> productIds
) {
}
