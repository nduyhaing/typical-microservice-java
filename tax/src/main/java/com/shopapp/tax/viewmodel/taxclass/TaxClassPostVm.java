package com.shopapp.tax.viewmodel.taxclass;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** YAS also had a required {@code id} field that it never used; dropped. */
public record TaxClassPostVm(@NotBlank @Size(max = 255) String name) {
}
