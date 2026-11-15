package com.shopapp.order.service;

import com.shopapp.commonlibrary.exception.BadRequestException;
import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.order.config.OrderServiceUrls;
import com.shopapp.order.utils.Constants;
import com.shopapp.order.viewmodel.external.ProductCheckoutListVm;
import com.shopapp.order.viewmodel.external.ProductGetCheckoutListVm;
import com.shopapp.order.viewmodel.external.ProductQuantityVm;
import com.shopapp.order.viewmodel.external.ProductVariationIdVm;
import com.shopapp.order.viewmodel.external.RemoteErrorVm;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Prices products at checkout through product GET /products (YAS), relaying the customer's token. Only orderable
 * products come back (decisions.md 2c-2), so a missing id means "cannot be ordered". Takes and gives back stock for
 * an order with subtract-quantity / restore-quantity under a server-side reference (3c-29, 3c-45).
 */
@Slf4j
@Service
public class ProductService {

    private static final ParameterizedTypeReference<List<ProductVariationIdVm>> VARIATIONS =
        new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public ProductService(OrderServiceUrls urls) {
        this.restClient = RestClients.withTimeouts(urls.product());
    }

    public Map<Long, ProductCheckoutListVm> getOrderableProducts(Collection<Long> ids) {
        try {
            ProductGetCheckoutListVm response = restClient.get()
                .uri(uri -> uri.path("/products").queryParam("ids", ids).queryParam("pageSize", ids.size()).build())
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .body(ProductGetCheckoutListVm.class);
            List<ProductCheckoutListVm> products = response == null || response.productCheckoutListVms() == null
                ? List.of() : response.productCheckoutListVms();
            return products.stream().collect(Collectors.toMap(ProductCheckoutListVm::id, Function.identity()));
        } catch (RestClientException ex) {
            log.error("Product service unavailable: {}", ex.getMessage());
            throw new InternalServerErrorException("Product service is unavailable, please try again later");
        }
    }

    /**
     * Takes the units of an order (product ignores untracked products). Not enough stock is a 400 naming the product;
     * nothing is taken then. Any other failure is a 500, and the reservation stays "may hold stock" unless product
     * answered 4xx, so the caller restores it.
     *
     * @param productNames names for the error message, by product id
     */
    public void subtractStock(StockReservation reservation, List<ProductQuantityVm> items,
                              Map<Long, String> productNames) {
        reservation.sent();
        try {
            restClient.put()
                .uri(uri -> uri.path("/backoffice/products/subtract-quantity")
                    .queryParam("reference", reservation.reference()).build())
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .body(items)
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            // a 4xx answer means product changed nothing (3c-21)
            reservation.refused();
            RemoteErrorVm error = errorBody(ex);
            if (error != null && Constants.ErrorCode.NOT_ENOUGH_STOCK.equals(error.title())) {
                Long productId = productIdOf(error);
                throw new BadRequestException(Constants.ErrorCode.NOT_ENOUGH_STOCK,
                    productNames.getOrDefault(productId, "a product"), productId);
            }
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new BadRequestException(Constants.ErrorCode.PRODUCTS_NO_LONGER_AVAILABLE);
            }
            log.error("Product refused the stock subtraction: {}", ex.getMessage());
            throw new InternalServerErrorException("Product service is unavailable, please try again later");
        } catch (RestClientException ex) {
            log.error("Product service unavailable while taking stock: {}", ex.getMessage());
            throw new InternalServerErrorException("Product service is unavailable, please try again later");
        }
    }

    /**
     * The ids of the product's published variations (YAS order asks product the same way), with the caller's token;
     * empty when it has none or product does not know it (404). Product down is a 500.
     */
    public List<Long> getVariationIds(Long productId) {
        try {
            List<ProductVariationIdVm> variations = restClient.get()
                .uri("/storefront/product-variations/{id}", productId)
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .body(VARIATIONS);
            return variations == null ? List.of() : variations.stream().map(ProductVariationIdVm::id).toList();
        } catch (HttpClientErrorException ex) {
            if (ex.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                return List.of();
            }
            log.error("Product refused the variation lookup of {}: {}", productId, ex.getMessage());
            throw new InternalServerErrorException("Product service is unavailable, please try again later");
        } catch (RestClientException ex) {
            log.error("Product service unavailable: {}", ex.getMessage());
            throw new InternalServerErrorException("Product service is unavailable, please try again later");
        }
    }

    /** Compensation: gives back what {@link #subtractStock} took under the reference. Best effort, logged. */
    public void restoreStock(StockReservation reservation) {
        try {
            restClient.put()
                .uri(uri -> uri.path("/backoffice/products/restore-quantity")
                    .queryParam("reference", reservation.reference()).build())
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("Could not give back the stock of failed order attempt {}: {}", reservation.reference(),
                ex.getMessage());
        }
    }

    private static RemoteErrorVm errorBody(HttpClientErrorException ex) {
        try {
            return ex.getResponseBodyAs(RemoteErrorVm.class);
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }

    /** {@code fieldErrors: ["productId 25"]} (3c-21). */
    private static Long productIdOf(RemoteErrorVm error) {
        if (error.fieldErrors() == null) {
            return null;
        }
        return error.fieldErrors().stream()
            .filter(field -> field != null && field.startsWith("productId "))
            .map(field -> field.substring("productId ".length()).trim())
            .filter(id -> id.matches("[0-9]{1,18}"))
            .map(Long::valueOf)
            .findFirst()
            .orElse(null);
    }
}
