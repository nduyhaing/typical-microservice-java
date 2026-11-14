package com.shopapp.cart.viewmodel;

import com.shopapp.cart.utils.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

@Builder
public record CartItemPostVm(
    @NotNull Long productId,
    @NotNull @Min(1) @Max(Constants.MAX_CART_QUANTITY) Integer quantity) {}
