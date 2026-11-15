package com.shopapp.location.controller;

import com.shopapp.location.model.Country;
import com.shopapp.location.service.CountryService;
import com.shopapp.location.utils.Constants;
import com.shopapp.location.viewmodel.country.CountryListGetVm;
import com.shopapp.location.viewmodel.country.CountryPostVm;
import com.shopapp.location.viewmodel.country.CountryVm;
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
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping(Constants.ApiConstant.COUNTRIES_URL)
@RequiredArgsConstructor
public class CountryController {

    private final CountryService countryService;

    @GetMapping("/paging")
    public ResponseEntity<CountryListGetVm> getPageableCountries(
        @RequestParam(value = "pageNo", defaultValue = "0")
        @Min(0) @Max(Constants.Limits.MAX_PAGE_NO) int pageNo,
        @RequestParam(value = "pageSize", defaultValue = "10")
        @Min(1) @Max(Constants.Limits.MAX_PAGE_SIZE) int pageSize) {
        return ResponseEntity.ok(countryService.getPageableCountries(pageNo, pageSize));
    }

    @GetMapping
    public ResponseEntity<List<CountryVm>> listCountries() {
        return ResponseEntity.ok(countryService.findAllCountries());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CountryVm> getCountry(@PathVariable("id") Long id) {
        return ResponseEntity.ok(countryService.findById(id));
    }

    @PostMapping
    public ResponseEntity<CountryVm> createCountry(@Valid @RequestBody CountryPostVm countryPostVm,
                                                   UriComponentsBuilder uriComponentsBuilder) {
        Country country = countryService.create(countryPostVm);
        return ResponseEntity.created(uriComponentsBuilder.replacePath("/countries/{id}")
                .buildAndExpand(country.getId()).toUri())
            .body(CountryVm.fromModel(country));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updateCountry(@PathVariable Long id,
                                              @Valid @RequestBody CountryPostVm countryPostVm) {
        countryService.update(countryPostVm, id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCountry(@PathVariable(name = "id") Long id) {
        countryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
