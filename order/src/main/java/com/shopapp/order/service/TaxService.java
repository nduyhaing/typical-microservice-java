package com.shopapp.order.service;

import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.order.config.OrderServiceUrls;
import com.shopapp.order.viewmodel.external.TaxRateVm;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Tax rates for an address from tax GET /backoffice/tax-rates/location-based-batch, one call per order with every
 * tax class of its lines and the customer's token (decisions.md 3c-13, 3c-44). The batch answers the most specific
 * rate per class and leaves out classes without a rate, which pay 0 %. Any failure is a 500: no order is priced
 * without tax.
 */
@Slf4j
@Service
public class TaxService {

    private static final ParameterizedTypeReference<List<TaxRateVm>> RATES = new ParameterizedTypeReference<>() {
    };

    private final RestClient restClient;

    public TaxService(OrderServiceUrls urls) {
        this.restClient = RestClients.withTimeouts(urls.tax());
    }

    /** Percent per tax class id; empty without classes (no call). */
    public Map<Long, BigDecimal> getRates(Collection<Long> taxClassIds, Long countryId, Long stateOrProvinceId,
                                          String zipCode) {
        if (taxClassIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<TaxRateVm> rates = restClient.get()
                .uri(uri -> {
                    uri.path("/backoffice/tax-rates/location-based-batch")
                        .queryParam("taxClassIds", taxClassIds)
                        .queryParam("countryId", countryId);
                    if (stateOrProvinceId != null) {
                        uri.queryParam("stateOrProvinceId", stateOrProvinceId);
                    }
                    if (zipCode != null && !zipCode.isBlank()) {
                        uri.queryParam("zipCode", zipCode.trim());
                    }
                    return uri.build();
                })
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .body(RATES);
            return rates == null ? Map.of() : rates.stream()
                .filter(rate -> rate.taxClassId() != null && rate.rate() != null)
                .collect(Collectors.toMap(TaxRateVm::taxClassId, TaxRateVm::rate, (first, second) -> first));
        } catch (RestClientException ex) {
            log.error("Tax service unavailable: {}", ex.getMessage());
            throw new InternalServerErrorException("Tax service is unavailable, please try again later");
        }
    }
}
