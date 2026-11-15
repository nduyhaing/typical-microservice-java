package com.shopapp.inventory.viewmodel.address;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The part of the created-address answer of location that inventory needs. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AddressVm(Long id) {
}
