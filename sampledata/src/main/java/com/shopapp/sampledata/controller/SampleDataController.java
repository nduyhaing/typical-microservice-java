package com.shopapp.sampledata.controller;

import com.shopapp.sampledata.service.SampleDataService;
import com.shopapp.sampledata.viewmodel.SampleDataVm;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SampleDataController {

    private final SampleDataService sampleDataService;

    public SampleDataController(SampleDataService sampleDataService) {
        this.sampleDataService = sampleDataService;
    }

    /** YAS {@code POST /storefront/sampledata} (public, with an unused body); here admin only, no body (5-50). */
    @PostMapping("/backoffice/sampledata")
    @Operation(summary = "Insert the sample catalog rows that are missing and republish soft-deleted sample products")
    public ResponseEntity<SampleDataVm> createSampleData() {
        return ResponseEntity.ok(sampleDataService.createSampleData());
    }
}
