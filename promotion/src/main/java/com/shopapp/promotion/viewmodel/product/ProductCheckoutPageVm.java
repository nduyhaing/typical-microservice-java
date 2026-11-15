package com.shopapp.promotion.viewmodel.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductCheckoutPageVm(List<ProductCheckoutVm> productCheckoutListVms) {
}
