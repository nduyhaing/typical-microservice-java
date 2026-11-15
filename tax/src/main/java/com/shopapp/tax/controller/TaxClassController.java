package com.shopapp.tax.controller;

import com.shopapp.commonlibrary.constants.PageableConstant;
import com.shopapp.tax.model.TaxClass;
import com.shopapp.tax.service.TaxClassService;
import com.shopapp.tax.utils.Constants;
import com.shopapp.tax.viewmodel.taxclass.TaxClassListGetVm;
import com.shopapp.tax.viewmodel.taxclass.TaxClassPostVm;
import com.shopapp.tax.viewmodel.taxclass.TaxClassVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping("/backoffice/tax-classes")
@RequiredArgsConstructor
public class TaxClassController {

    private final TaxClassService taxClassService;

    @GetMapping("/paging")
    public ResponseEntity<TaxClassListGetVm> getPageableTaxClasses(
        @RequestParam(value = "pageNo", defaultValue = PageableConstant.DEFAULT_PAGE_NUMBER, required = false)
        @Min(0) @Max(Constants.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = PageableConstant.DEFAULT_PAGE_SIZE, required = false)
        @Min(1) @Max(Constants.MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(taxClassService.getPageableTaxClasses(pageNo, pageSize));
    }

    @GetMapping
    public ResponseEntity<List<TaxClassVm>> listTaxClasses() {
        return ResponseEntity.ok(taxClassService.findAllTaxClasses());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaxClassVm> getTaxClass(@PathVariable("id") Long id) {
        return ResponseEntity.ok(taxClassService.findById(id));
    }

    @PostMapping
    public ResponseEntity<TaxClassVm> createTaxClass(@Valid @RequestBody TaxClassPostVm taxClassPostVm) {
        TaxClass taxClass = taxClassService.create(taxClassPostVm);
        return ResponseEntity
            .created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(taxClass.getId()).toUri())
            .body(TaxClassVm.fromModel(taxClass));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateTaxClass(@PathVariable("id") Long id,
                                               @Valid @RequestBody TaxClassPostVm taxClassPostVm) {
        taxClassService.update(taxClassPostVm, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTaxClass(@PathVariable("id") Long id) {
        taxClassService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
