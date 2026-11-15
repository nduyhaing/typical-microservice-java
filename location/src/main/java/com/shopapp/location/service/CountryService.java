package com.shopapp.location.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.DuplicatedException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.location.model.Country;
import com.shopapp.location.repository.AddressRepository;
import com.shopapp.location.repository.CountryRepository;
import com.shopapp.location.repository.StateOrProvinceRepository;
import com.shopapp.location.utils.Constants;
import com.shopapp.location.viewmodel.country.CountryListGetVm;
import com.shopapp.location.viewmodel.country.CountryPostVm;
import com.shopapp.location.viewmodel.country.CountryVm;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CountryService {

    private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name");

    private final CountryRepository countryRepository;
    private final StateOrProvinceRepository stateOrProvinceRepository;
    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<CountryVm> findAllCountries() {
        return countryRepository.findAll(BY_NAME).stream().map(CountryVm::fromModel).toList();
    }

    @Transactional(readOnly = true)
    public CountryVm findById(Long id) {
        return CountryVm.fromModel(getCountry(id));
    }

    public Country create(CountryPostVm request) {
        String name = request.name().trim();
        String code2 = request.code2().trim();
        if (countryRepository.existsByCode2IgnoreCase(code2)) {
            throw new DuplicatedException(Constants.ErrorCode.CODE_ALREADY_EXISTED, code2);
        }
        if (countryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicatedException(Constants.ErrorCode.NAME_ALREADY_EXITED, name);
        }
        Country country = new Country();
        apply(country, request);
        return countryRepository.saveAndFlush(country);
    }

    public void update(CountryPostVm request, Long id) {
        Country country = getCountry(id);
        String name = request.name().trim();
        String code2 = request.code2().trim();
        if (countryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new DuplicatedException(Constants.ErrorCode.NAME_ALREADY_EXITED, name);
        }
        if (countryRepository.existsByCode2IgnoreCaseAndIdNot(code2, id)) {
            throw new DuplicatedException(Constants.ErrorCode.CODE_ALREADY_EXISTED, code2);
        }
        apply(country, request);
        countryRepository.saveAndFlush(country);
    }

    public void delete(Long id) {
        if (!countryRepository.existsById(id)) {
            throw new NotFoundException(Constants.ErrorCode.COUNTRY_NOT_FOUND, id);
        }
        if (stateOrProvinceRepository.existsByCountryId(id) || addressRepository.existsByCountryId(id)) {
            throw new BadRequestException(Constants.ErrorCode.COUNTRY_IN_USE, id);
        }
        countryRepository.deleteById(id);
        countryRepository.flush();
    }

    @Transactional(readOnly = true)
    public CountryListGetVm getPageableCountries(int pageNo, int pageSize) {
        Page<Country> page = countryRepository.findAll(PageRequest.of(pageNo, pageSize, BY_NAME));
        return new CountryListGetVm(
            page.getContent().stream().map(CountryVm::fromModel).toList(),
            page.getNumber(),
            page.getSize(),
            (int) page.getTotalElements(),
            page.getTotalPages(),
            page.isLast());
    }

    private Country getCountry(Long id) {
        return countryRepository.findById(id)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.COUNTRY_NOT_FOUND, id));
    }

    /** All fields are replaced (YAS's mapper ignored nulls, so a flag could never be cleared). */
    private static void apply(Country country, CountryPostVm request) {
        country.setName(request.name().trim());
        country.setCode2(request.code2().trim());
        country.setCode3(request.code3() == null || request.code3().isBlank() ? null : request.code3().trim());
        country.setIsBillingEnabled(request.isBillingEnabled());
        country.setIsShippingEnabled(request.isShippingEnabled());
        country.setIsCityEnabled(request.isCityEnabled());
        country.setIsZipCodeEnabled(request.isZipCodeEnabled());
        country.setIsDistrictEnabled(request.isDistrictEnabled());
    }
}
