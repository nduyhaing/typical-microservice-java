package com.shopapp.tax.controller;

import com.shopapp.commonlibrary.constants.PageableConstant;
import com.shopapp.tax.model.TaxRate;
import com.shopapp.tax.service.TaxRateService;
import com.shopapp.tax.utils.Constants;
import com.shopapp.tax.viewmodel.taxrate.TaxRateListGetVm;
import com.shopapp.tax.viewmodel.taxrate.TaxRatePostVm;
import com.shopapp.tax.viewmodel.taxrate.TaxRateVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/backoffice/tax-rates")
@RequiredArgsConstructor
public class TaxRateController {

    private final TaxRateService taxRateService;

    @GetMapping("/paging")
    public ResponseEntity<TaxRateListGetVm> getPageableTaxRates(
        @RequestParam(value = "pageNo", defaultValue = PageableConstant.DEFAULT_PAGE_NUMBER, required = false)
        @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = PageableConstant.DEFAULT_PAGE_SIZE, required = false)
        @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(taxRateService.getPageableTaxRates(pageNo, pageSize));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaxRateVm> getTaxRate(@PathVariable("id") Long id) {
        return ResponseEntity.ok(taxRateService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TaxRateVm> createTaxRate(@Valid @RequestBody TaxRatePostVm taxRatePostVm) {
        TaxRate taxRate = taxRateService.createTaxRate(taxRatePostVm);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(taxRate.getId()).toUri())
            .body(TaxRateVm.fromModel(taxRate));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTaxRate(@PathVariable("id") Long id,
                                              @Valid @RequestBody TaxRatePostVm taxRatePostVm) {
        taxRateService.updateTaxRate(taxRatePostVm, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTaxRate(@PathVariable("id") Long id) {
        taxRateService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Rate in percent for one tax class at an address; 0.0 when no rate applies. Any authenticated caller. */
    @GetMapping("/tax-percent")
    public ResponseEntity<BigDecimal> getTaxPercentByAddress(
        @RequestParam("taxClassId") Long taxClassId,
        @RequestParam("countryId") Long countryId,
        @RequestParam(value = "stateOrProvinceId", required = false) Long stateOrProvinceId,
        @RequestParam(value = "zipCode", required = false) @Size(max = Constants.MAX_ZIP_CODE_LENGTH) String zipCode) {
        return ResponseEntity.ok(taxRateService.getTaxPercent(taxClassId, countryId, stateOrProvinceId, zipCode));
    }

    /** The applicable rate per tax class at an address (classes without a rate are left out). Any authenticated. */
    @GetMapping("/location-based-batch")
    public ResponseEntity<List<TaxRateVm>> getBatchTaxPercentsByAddress(
        @RequestParam("taxClassIds") @Size(min = 1, max = Constants.MAX_BATCH_TAX_CLASSES) List<Long> taxClassIds,
        @RequestParam("countryId") Long countryId,
        @RequestParam(value = "stateOrProvinceId", required = false) Long stateOrProvinceId,
        @RequestParam(value = "zipCode", required = false) @Size(max = Constants.MAX_ZIP_CODE_LENGTH) String zipCode) {
        return ResponseEntity.ok(taxRateService.getBulkTaxRate(taxClassIds, countryId, stateOrProvinceId, zipCode));
    }
}
