package com.shopapp.promotion.viewmodel.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A brand or category from product's storefront lists: only id and slug are used. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SlugVm(Long id, String slug) {
}
