package com.shopapp.cart.viewmodel;

import com.shopapp.cart.utils.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemPutVm(@NotNull @Min(1) @Max(Constants.MAX_CART_QUANTITY) Integer quantity) {}