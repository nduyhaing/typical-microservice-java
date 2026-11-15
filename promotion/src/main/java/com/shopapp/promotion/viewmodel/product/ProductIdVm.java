package com.shopapp.promotion.viewmodel.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductIdVm(Long id) {
}
