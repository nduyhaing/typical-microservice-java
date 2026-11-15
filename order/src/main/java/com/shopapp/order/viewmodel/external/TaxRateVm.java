package com.shopapp.order.viewmodel.external;

import java.math.BigDecimal;

/** One entry of tax GET /backoffice/tax-rates/location-based-batch: the rate (percent) of a tax class. */
public record TaxRateVm(Long taxClassId, BigDecimal rate) {
}
