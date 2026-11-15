package com.shopapp.inventory.viewmodel.stock;

import com.shopapp.inventory.utils.Constants;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record StockQuantityUpdateVm(
    @NotEmpty @Size(max = Constants.MAX_ITEMS) List<@Valid StockQuantityVm> stockQuantityList) {
}
