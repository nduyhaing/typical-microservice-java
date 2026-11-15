package com.shopapp.location.controller;

import com.shopapp.location.service.StateOrProvinceService;
import com.shopapp.location.utils.Constants;
import com.shopapp.location.viewmodel.stateorprovince.StateOrProvinceVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(Constants.ApiConstant.STATE_OR_PROVINCES_STOREFRONT_URL)
public class StateOrProvinceStoreFrontController {

    private final StateOrProvinceService stateOrProvinceService;

    @GetMapping("/{countryId}")
    public ResponseEntity<List<StateOrProvinceVm>> getStateOrProvince(@PathVariable("countryId") Long id) {
        return ResponseEntity.ok(stateOrProvinceService.getAllByCountryId(id));
    }
}
