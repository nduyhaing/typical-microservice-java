package com.shopapp.location.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.location.model.Address;
import com.shopapp.location.model.Country;
import com.shopapp.location.model.District;
import com.shopapp.location.model.StateOrProvince;
import com.shopapp.location.repository.AddressRepository;
import com.shopapp.location.repository.CountryRepository;
import com.shopapp.location.repository.DistrictRepository;
import com.shopapp.location.repository.StateOrProvinceRepository;
import com.shopapp.location.utils.Constants;
import com.shopapp.location.viewmodel.address.AddressDetailVm;
import com.shopapp.location.viewmodel.address.AddressGetVm;
import com.shopapp.location.viewmodel.address.AddressPostVm;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saved addresses. The creating user (JWT {@code sub}, stored in {@code createdBy}) owns an address; only the owner
 * or an admin can read, change or delete it. Someone else's address answers 404 like a missing one, so ids
 * cannot be probed (YAS: anyone, even anonymous, could use any address id).
 */
@Service
@Transactional
@RequiredArgsConstructor
public class AddressService {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AddressRepository addressRepository;
    private final StateOrProvinceRepository stateOrProvinceRepository;
    private final CountryRepository countryRepository;
    private final DistrictRepository districtRepository;

    public AddressGetVm createAddress(AddressPostVm request) {
        Address address = new Address();
        apply(address, request);
        return AddressGetVm.fromModel(addressRepository.saveAndFlush(address));
    }

    public void updateAddress(Long id, AddressPostVm request) {
        Address address = getAccessibleAddress(id);
        apply(address, request);
        addressRepository.saveAndFlush(address);
    }

    @Transactional(readOnly = true)
    public List<AddressDetailVm> getAddressList(List<Long> ids) {
        return addressRepository.findAllByIdInOrderById(ids).stream()
            .filter(AddressService::isAccessible)
            .map(AddressDetailVm::fromModel)
            .toList();
    }

    @Transactional(readOnly = true)
    public AddressDetailVm getAddress(Long id) {
        return AddressDetailVm.fromModel(getAccessibleAddress(id));
    }

    public void deleteAddress(Long id) {
        addressRepository.delete(getAccessibleAddress(id));
        addressRepository.flush();
    }

    private Address getAccessibleAddress(Long id) {
        return addressRepository.findWithLocationById(id)
            .filter(AddressService::isAccessible)
            .orElseThrow(() -> new NotFoundException(Constants.ErrorCode.ADDRESS_NOT_FOUND, id));
    }

    private static boolean isAccessible(Address address) {
        Authentication authentication = AuthenticationUtils.getAuthentication();
        boolean admin = authentication.getAuthorities().stream()
            .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
        return admin || Objects.equals(address.getCreatedBy(), AuthenticationUtils.extractUserId());
    }

    /**
     * Copies the body onto the address. The country must exist; a state must belong to that country and a district
     * to that state (YAS ignored unknown state/district ids and failed later on the not-null columns).
     */
    private void apply(Address address, AddressPostVm request) {
        Country country = countryRepository.findById(request.countryId())
            .orElseThrow(() -> new BadRequestException(Constants.ErrorCode.COUNTRY_NOT_FOUND, request.countryId()));
        StateOrProvince state = null;
        if (request.stateOrProvinceId() != null) {
            state = stateOrProvinceRepository.findById(request.stateOrProvinceId())
                .orElseThrow(() -> new BadRequestException(Constants.ErrorCode.STATE_OR_PROVINCE_NOT_FOUND,
                    request.stateOrProvinceId()));
            if (!state.getCountry().getId().equals(country.getId())) {
                throw new BadRequestException(Constants.ErrorCode.STATE_NOT_IN_COUNTRY, state.getId(),
                    country.getId());
            }
        }
        District district = null;
        if (request.districtId() != null) {
            district = districtRepository.findById(request.districtId())
                .orElseThrow(() -> new BadRequestException(Constants.ErrorCode.DISTRICT_NOT_FOUND,
                    request.districtId()));
            if (state == null || !district.getStateProvince().getId().equals(state.getId())) {
                throw new BadRequestException(Constants.ErrorCode.DISTRICT_NOT_IN_STATE, district.getId(),
                    request.stateOrProvinceId());
            }
        }
        address.setContactName(request.contactName().trim());
        address.setPhone(request.phone().trim());
        address.setAddressLine1(request.addressLine1().trim());
        address.setAddressLine2(blankToNull(request.addressLine2()));
        address.setCity(blankToNull(request.city()));
        address.setZipCode(blankToNull(request.zipCode()));
        address.setCountry(country);
        address.setStateOrProvince(state);
        address.setDistrict(district);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
