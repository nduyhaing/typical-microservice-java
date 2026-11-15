package com.shopapp.rating.viewmodel;

import com.shopapp.rating.utils.Constants;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * YAS RatingPostVm. {@code productName} is accepted for compatibility but ignored: the name is taken from the product
 * service (decisions.md 4-31).
 */
public record RatingPostVm(
    @NotBlank @Size(max = Constants.MAX_CONTENT_LENGTH) String content,
    @Min(1) @Max(5) int star,
    @NotNull @Positive Long productId,
    String productName
) {
}
