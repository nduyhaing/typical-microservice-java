package com.shopapp.inventory.viewmodel.stock;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One adjustment: add (positive) or remove (negative) units of a stock row, with a note for the history. */
public record StockQuantityVm(@NotNull Long stockId,
                              @NotNull @Min(-1_000_000_000) @Max(1_000_000_000) Long quantity,
                              @Size(max = 450) String note) {
}
