package com.shopapp.location.controller;

import com.shopapp.location.model.StateOrProvince;
import com.shopapp.location.service.StateOrProvinceService;
import com.shopapp.location.utils.Constants;
import com.shopapp.location.viewmodel.stateorprovince.StateOrProvinceAndCountryGetNameVm;
import com.shopapp.location.viewmodel.stateorprovince.StateOrProvinceListGetVm;
import com.shopapp.location.viewmodel.stateorprovince.StateOrProvincePostVm;
import com.shopapp.location.viewmodel.stateorprovince.StateOrProvinceVm;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping(Constants.ApiConstant.STATE_OR_PROVINCES_URL)
@RequiredArgsConstructor
public class StateOrProvinceController {

    private final StateOrProvinceService stateOrProvinceService;

    @GetMapping("/paging")
    public ResponseEntity<StateOrProvinceListGetVm> getPageableStateOrProvinces(
        @RequestParam(value = "pageNo", defaultValue = "0")
        @Min(0) @Max(Constants.Limits.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "10")
        @Min(1) @Max(Constants.Limits.MAX_PAGE_SIZE) int pageSize,
        @RequestParam(value = "countryId", required = false) Long countryId) {
        return ResponseEntity.ok(stateOrProvinceService.getPageableStateOrProvinces(pageNo, pageSize, countryId));
    }

    @GetMapping
    public ResponseEntity<List<StateOrProvinceVm>> getAllByCountryId(
        @RequestParam(value = "countryId", required = false) Long countryId) {
        return ResponseEntity.ok(stateOrProvinceService.getAllByCountryId(countryId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StateOrProvinceVm> getStateOrProvince(@PathVariable("id") Long id) {
        return ResponseEntity.ok(stateOrProvinceService.findById(id));
    }

    /** State and country names for up to 100 state ids (unknown ids are skipped). */
    @GetMapping("/state-country-names")
    public ResponseEntity<List<StateOrProvinceAndCountryGetNameVm>> getStateOrProvinceAndCountryNames(
        @RequestParam(value = "stateOrProvinceIds") @Size(max = Constants.Limits.MAX_IDS) List<Long> ids) {
        return ResponseEntity.ok(stateOrProvinceService.getStateOrProvinceAndCountryNames(ids));
    }

    @PostMapping
    public ResponseEntity<StateOrProvinceVm> createStateOrProvince(
        @Valid @RequestBody StateOrProvincePostVm stateOrProvincePostVm,
        UriComponentsBuilder uriComponentsBuilder) {
        StateOrProvince stateOrProvince = stateOrProvinceService.createStateOrProvince(stateOrProvincePostVm);
        return ResponseEntity.created(uriComponentsBuilder.replacePath("/state-or-provinces/{id}")
                .buildAndExpand(stateOrProvince.getId()).toUri())
            .body(StateOrProvinceVm.fromModel(stateOrProvince));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateStateOrProvince(@PathVariable Long id,
                                                      @Valid @RequestBody StateOrProvincePostVm request) {
        stateOrProvinceService.updateStateOrProvince(request, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStateOrProvince(@PathVariable Long id) {
        stateOrProvinceService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
