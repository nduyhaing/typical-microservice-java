package com.shopapp.location.controller;

import com.shopapp.location.service.CountryService;
import com.shopapp.location.utils.Constants;
import com.shopapp.location.viewmodel.country.CountryVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(Constants.ApiConstant.COUNTRIES_STOREFRONT_URL)
@RequiredArgsConstructor
public class CountryStorefrontController {

    private final CountryService countryService;

    @GetMapping
    public ResponseEntity<List<CountryVm>> listCountries() {
        return ResponseEntity.ok(countryService.findAllCountries());
    }
}
