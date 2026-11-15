package com.shopapp.inventory.service;

import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.inventory.config.InventoryServiceUrls;
import com.shopapp.inventory.utils.Constants;
import com.shopapp.inventory.viewmodel.address.AddressDetailVm;
import com.shopapp.inventory.viewmodel.address.AddressPostVm;
import com.shopapp.inventory.viewmodel.address.AddressVm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Warehouse addresses in location {@code /storefront/addresses}, with the admin's relayed token (location lets ADMIN
 * use any address, decisions.md 3c-5).
 */
@Slf4j
@Service
public class LocationService {

    private static final String SERVICE = "Location";
    private static final String ADDRESSES = "/storefront/addresses";
    private static final String ADDRESS = ADDRESSES + "/{id}";

    private final RestClient restClient;

    public LocationService(InventoryServiceUrls urls) {
        this.restClient = RemoteCalls.restClient(urls.location());
    }

    public AddressVm createAddress(AddressPostVm address) {
        return RemoteCalls.call(SERVICE, () -> restClient.post()
                .uri(ADDRESSES)
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(address)
                .retrieve()
                .body(AddressVm.class),
            null);
    }

    public AddressDetailVm getAddress(Long id) {
        return RemoteCalls.call(SERVICE, () -> restClient.get()
                .uri(ADDRESS, id)
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .body(AddressDetailVm.class),
            () -> new NotFoundException(Constants.ErrorCode.ADDRESS_NOT_FOUND, id));
    }

    public void updateAddress(Long id, AddressPostVm address) {
        RemoteCalls.call(SERVICE, () -> restClient.put()
                .uri(ADDRESS, id)
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(address)
                .retrieve()
                .toBodilessEntity(),
            () -> new NotFoundException(Constants.ErrorCode.ADDRESS_NOT_FOUND, id));
    }

    /** Deletes the address; one that is already gone counts as deleted. */
    public void deleteAddress(Long id) {
        try {
            RemoteCalls.call(SERVICE, () -> restClient.delete()
                    .uri(ADDRESS, id)
                    .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                    .retrieve()
                    .toBodilessEntity(),
                () -> new NotFoundException(Constants.ErrorCode.ADDRESS_NOT_FOUND, id));
        } catch (NotFoundException ex) {
            log.info("Address {} was already deleted in location", id);
        }
    }

    /** Compensation after a failed save: logs instead of throwing, so the original error reaches the client. */
    public void deleteAddressQuietly(Long id) {
        try {
            deleteAddress(id);
        } catch (RuntimeException ex) {
            log.error("Could not delete orphan address {} in location: {}", id, ex.getMessage());
        }
    }
}
