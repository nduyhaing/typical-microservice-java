package com.shopapp.product.viewmodel.productoption;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public record ProductOptionPostVm(@NotBlank @Size(max = 255) String name) {
}
