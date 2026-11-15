package com.shopapp.customer.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.exception.NotFoundException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.commonlibrary.viewmodel.error.ErrorVm;
import com.shopapp.customer.config.CustomerServiceUrls;
import com.shopapp.customer.utils.Constants;
import com.shopapp.customer.viewmodel.address.AddressDetailVm;
import com.shopapp.customer.viewmodel.address.AddressPostVm;
import com.shopapp.customer.viewmodel.address.AddressVm;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Location {@code /storefront/addresses} with the caller's token (YAS LocationService): location makes the caller the
 * owner of a new address and hides other users' addresses (decisions.md 3c-5). Location's 400 is passed on as 400,
 * its 404 as "user address not found"; anything else (down, timeout, 5xx) is a 500 with a plain message.
 */
@Slf4j
@Service
public class LocationService {

    static final String UNAVAILABLE = "Location service is unavailable, please try again later";
    private static final String ADDRESSES = "/storefront/addresses";

    private final RestClient restClient;

    public LocationService(CustomerServiceUrls urls) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(5));
        this.restClient = RestClient.builder().baseUrl(urls.location()).requestFactory(requestFactory).build();
    }

    public AddressVm createAddress(AddressPostVm address) {
        return call(() -> restClient.post()
            .uri(ADDRESSES)
            .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
            .body(address)
            .retrieve()
            .body(AddressVm.class));
    }

    /** The caller's addresses among {@code ids}; ids of other users or of deleted addresses are left out. */
    public List<AddressDetailVm> getAddressesByIdList(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<AddressDetailVm> addresses = call(() -> restClient.get()
            .uri(uri -> uri.path(ADDRESSES).queryParam("ids", ids).build())
            .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
            .retrieve()
            .body(new ParameterizedTypeReference<List<AddressDetailVm>>() { }));
        return addresses == null ? List.of() : addresses;
    }

    public AddressDetailVm getAddressById(Long id) {
        return call(() -> restClient.get()
            .uri(ADDRESSES + "/{id}", id)
            .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
            .retrieve()
            .body(AddressDetailVm.class));
    }

    /** Deletes the address; one that is already gone counts as deleted. */
    public void deleteAddress(Long id) {
        try {
            call(() -> restClient.delete()
                .uri(ADDRESSES + "/{id}", id)
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .toBodilessEntity());
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

    private static <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new NotFoundException(Constants.ErrorCode.USER_ADDRESS_NOT_FOUND);
            }
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                throw new BadRequestException(detail(ex));
            }
            log.error("Location refused the request: {}", ex.getMessage());
            throw new InternalServerErrorException(UNAVAILABLE);
        } catch (RestClientException ex) {
            log.error("Location service unavailable: {}", ex.getMessage());
            throw new InternalServerErrorException(UNAVAILABLE);
        }
    }

    private static String detail(HttpClientErrorException ex) {
        try {
            ErrorVm error = ex.getResponseBodyAs(ErrorVm.class);
            if (error != null && error.detail() != null) {
                return error.detail();
            }
        } catch (RuntimeException ignored) {
            // not an ErrorVm body
        }
        return Constants.ErrorCode.INVALID_ADDRESS;
    }
}
