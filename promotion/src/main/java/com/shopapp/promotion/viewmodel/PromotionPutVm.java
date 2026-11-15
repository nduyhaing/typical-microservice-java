package com.shopapp.promotion.viewmodel;

import com.shopapp.promotion.model.enumeration.ApplyTo;
import com.shopapp.promotion.model.enumeration.DiscountType;
import com.shopapp.promotion.model.enumeration.UsageType;
import com.shopapp.promotion.utils.Constants;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Update a promotion; the id is in the body as in YAS ({@code PUT /backoffice/promotions}). */
public record PromotionPutVm(
    @NotNull Long id,
    @NotBlank @Size(max = 450) String name,
    @NotBlank @Size(max = 450)
    @Pattern(regexp = PromotionPostVm.SLUG, message = "must be lowercase letters, digits and dashes") String slug,
    @Size(max = 1000) String description,
    @NotBlank @Size(max = 100)
    @Pattern(regexp = PromotionPostVm.COUPON_CODE, message = "must be letters, digits, '-' or '_'") String couponCode,
    @NotNull DiscountType discountType,
    @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") @Digits(integer = 3, fraction = 2)
    BigDecimal discountPercentage,
    @DecimalMin(value = "0", inclusive = false) @Digits(integer = 17, fraction = 2) BigDecimal discountAmount,
    @NotNull UsageType usageType,
    Integer usageLimit,
    @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal minimumOrderPurchaseAmount,
    @NotNull Boolean isActive,
    @NotNull Instant startDate,
    @NotNull Instant endDate,
    @NotNull ApplyTo applyTo,
    @Size(max = Constants.MAX_IDS) List<@NotNull @Positive Long> productIds,
    @Size(max = Constants.MAX_IDS) List<@NotNull @Positive Long> brandIds,
    @Size(max = Constants.MAX_IDS) List<@NotNull @Positive Long> categoryIds
) implements PromotionInput {
}
