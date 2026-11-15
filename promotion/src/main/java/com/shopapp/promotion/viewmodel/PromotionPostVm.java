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

/**
 * Create a promotion (YAS PromotionPostVm). Dates are ISO-8601 instants (YAS: yyyy-MM-dd on create, a Date on
 * update); money and percentage are decimals (YAS: Long).
 */
public record PromotionPostVm(
    @NotBlank @Size(max = 450) String name,
    @NotBlank @Size(max = 450) @Pattern(regexp = SLUG, message = "must be lowercase letters, digits and dashes")
    String slug,
    @Size(max = 1000) String description,
    @NotBlank @Size(max = 100) @Pattern(regexp = COUPON_CODE, message = "must be letters, digits, '-' or '_'")
    String couponCode,
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

    static final String SLUG = "^[a-z0-9]+(-[a-z0-9]+)*$";
    static final String COUPON_CODE = "^[A-Za-z0-9_-]+$";
}
