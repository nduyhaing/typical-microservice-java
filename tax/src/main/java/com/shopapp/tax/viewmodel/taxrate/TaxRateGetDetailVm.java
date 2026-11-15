package com.shopapp.tax.viewmodel.taxrate;

import com.shopapp.tax.model.TaxRate;
import com.shopapp.tax.utils.Rates;
import java.math.BigDecimal;

/**
 * Row of the admin rate list. YAS put state and country names here (looked up in location); shop_app returns the
 * ids and the back office resolves the names (decisions.md 3c).
 */
public record TaxRateGetDetailVm(Long id, BigDecimal rate, String zipCode, Long taxClassId, String taxClassName,
                                 Long stateOrProvinceId, Long countryId) {

    public static TaxRateGetDetailVm fromModel(TaxRate taxRate) {
        return new TaxRateGetDetailVm(taxRate.getId(), Rates.display(taxRate.getRate()), taxRate.getZipCode(),
            taxRate.getTaxClass().getId(), taxRate.getTaxClass().getName(), taxRate.getStateOrProvinceId(),
            taxRate.getCountryId());
    }
}
