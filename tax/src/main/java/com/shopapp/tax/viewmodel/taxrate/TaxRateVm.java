package com.shopapp.tax.viewmodel.taxrate;

import com.shopapp.tax.model.TaxRate;
import com.shopapp.tax.utils.Rates;
import java.math.BigDecimal;

public record TaxRateVm(Long id, BigDecimal rate, String zipCode, Long taxClassId, Long stateOrProvinceId,
                        Long countryId) {

    public static TaxRateVm fromModel(TaxRate taxRate) {
        return new TaxRateVm(taxRate.getId(), Rates.display(taxRate.getRate()), taxRate.getZipCode(),
            taxRate.getTaxClass().getId(), taxRate.getStateOrProvinceId(), taxRate.getCountryId());
    }
}
