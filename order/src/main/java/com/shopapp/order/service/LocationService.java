package com.shopapp.order.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.order.config.OrderServiceUrls;
import com.shopapp.order.model.OrderAddress;
import com.shopapp.order.utils.Constants;
import com.shopapp.order.viewmodel.external.LocationAddressVm;
import com.shopapp.order.viewmodel.external.LocationItemVm;
import com.shopapp.order.viewmodel.order.OrderAddressPostVm;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * The order's shipping address from location (decisions.md 3c-35, 3c-41): a saved address is read with the
 * customer's token from GET /storefront/addresses/{id} (location answers 404 for another user's address, so nobody
 * can ship to someone else's address book); a typed address's country/state/district ids are checked against the
 * public lookups and their names taken from there. Location down is a 500.
 */
@Slf4j
@Service
public class LocationService {

    private static final ParameterizedTypeReference<List<LocationItemVm>> ITEMS = new ParameterizedTypeReference<>() {
    };
    private static final String UNAVAILABLE = "Location service is unavailable, please try again later";

    private final RestClient restClient;

    public LocationService(OrderServiceUrls urls) {
        this.restClient = RestClients.withTimeouts(urls.location());
    }

    /** One of the caller's saved addresses as an order address; 404 for unknown or someone else's. */
    public OrderAddress getSavedAddress(Long addressId) {
        LocationAddressVm address;
        try {
            address = restClient.get()
                .uri("/storefront/addresses/{id}", addressId)
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .body(LocationAddressVm.class);
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new NotFoundException(Constants.ErrorCode.ADDRESS_NOT_FOUND, addressId);
            }
            throw unavailable(ex);
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
        if (address == null || address.countryId() == null) {
            throw new NotFoundException(Constants.ErrorCode.ADDRESS_NOT_FOUND, addressId);
        }
        return OrderAddress.builder()
            .contactName(address.contactName()).phone(address.phone())
            .addressLine1(address.addressLine1()).addressLine2(address.addressLine2())
            .city(address.city()).zipCode(address.zipCode())
            .districtId(address.districtId()).districtName(address.districtName())
            .stateOrProvinceId(address.stateOrProvinceId()).stateOrProvinceName(address.stateOrProvinceName())
            .countryId(address.countryId()).countryName(address.countryName())
            .build();
    }

    /** A typed address with its location ids checked (400 when unknown or not nested) and names filled in. */
    public OrderAddress resolveTypedAddress(OrderAddressPostVm typed) {
        LocationItemVm country = find(lookup("/storefront/countries"), typed.countryId());
        if (country == null) {
            throw new BadRequestException(Constants.ErrorCode.COUNTRY_NOT_FOUND, typed.countryId());
        }
        LocationItemVm state = null;
        if (typed.stateOrProvinceId() != null) {
            state = find(lookup("/storefront/state-or-provinces/" + country.id()), typed.stateOrProvinceId());
            if (state == null) {
                throw new BadRequestException(Constants.ErrorCode.STATE_NOT_IN_COUNTRY, typed.stateOrProvinceId(),
                    country.id());
            }
        }
        LocationItemVm district = null;
        if (typed.districtId() != null) {
            if (state == null) {
                throw new BadRequestException(Constants.ErrorCode.DISTRICT_NEEDS_STATE, typed.districtId());
            }
            district = find(lookup("/storefront/district/" + state.id()), typed.districtId());
            if (district == null) {
                throw new BadRequestException(Constants.ErrorCode.DISTRICT_NOT_IN_STATE, typed.districtId(),
                    state.id());
            }
        }
        return OrderAddress.builder()
            .contactName(typed.contactName().trim()).phone(typed.phone().trim())
            .addressLine1(typed.addressLine1().trim()).addressLine2(blankToNull(typed.addressLine2()))
            .city(blankToNull(typed.city())).zipCode(blankToNull(typed.zipCode()))
            .districtId(district == null ? null : district.id())
            .districtName(district == null ? null : district.name())
            .stateOrProvinceId(state == null ? null : state.id())
            .stateOrProvinceName(state == null ? null : state.name())
            .countryId(country.id()).countryName(country.name())
            .build();
    }

    private List<LocationItemVm> lookup(String path) {
        try {
            List<LocationItemVm> items = restClient.get().uri(path).retrieve().body(ITEMS);
            return items == null ? List.of() : items;
        } catch (RestClientException ex) {
            throw unavailable(ex);
        }
    }

    private static LocationItemVm find(List<LocationItemVm> items, Long id) {
        return items.stream().filter(item -> Objects.equals(item.id(), id)).findFirst().orElse(null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static InternalServerErrorException unavailable(RestClientException ex) {
        log.error("Location service unavailable: {}", ex.getMessage());
        return new InternalServerErrorException(UNAVAILABLE);
    }
}
