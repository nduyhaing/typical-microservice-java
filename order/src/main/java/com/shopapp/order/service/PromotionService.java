package com.shopapp.order.service;

import com.shopapp.commonlibrary.exception.InternalServerErrorException;
import com.shopapp.commonlibrary.utils.AuthenticationUtils;
import com.shopapp.order.config.OrderServiceUrls;
import com.shopapp.order.exception.CouponRejectedException;
import com.shopapp.order.viewmodel.external.PromotionApplyUsageVm;
import com.shopapp.order.viewmodel.external.PromotionVerifyResultVm;
import com.shopapp.order.viewmodel.external.PromotionVerifyVm;
import com.shopapp.order.viewmodel.external.RemoteErrorVm;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Coupons through promotion (decisions.md 4-46, 4-50..4-55), relaying the customer's token: verify prices nothing and
 * takes nothing; apply-usage takes one use under a server-side reference before the order is committed; release-usage
 * gives it back when the order is not saved. Promotion's 400 answers become {@link CouponRejectedException} with
 * promotion's code; anything else (down, timeout, 5xx, other 4xx) is a 500.
 */
@Slf4j
@Service
public class PromotionService {

    static final String UNAVAILABLE = "Promotion service is unavailable, please try again later";
    static final String NOT_APPLICABLE = "PROMOTION_NOT_APPLICABLE";
    private static final String NOT_FOUND = "PROMOTION_NOT_FOUND";
    private static final String INVALID_CODE = "This coupon code is not valid";
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z_]{2,63}");

    private final RestClient restClient;
    private final ServiceTokenProvider serviceTokens;

    public PromotionService(OrderServiceUrls urls, ServiceTokenProvider serviceTokens) {
        this.restClient = RestClients.withTimeouts(urls.promotion());
        this.serviceTokens = serviceTokens;
    }

    /**
     * Checks a coupon against the checkout: its pre-discount subtotal and every product of it.
     *
     * @return the coupon with the eligible products
     */
    public Coupon verify(String couponCode, BigDecimal subtotal, List<Long> productIds) {
        if (subtotal.signum() <= 0 || productIds.isEmpty()) {
            // promotion needs a positive order price; a free order has nothing to discount
            throw new CouponRejectedException(NOT_APPLICABLE, "This coupon does not apply to this order");
        }
        PromotionVerifyResultVm result;
        try {
            result = restClient.post()
                .uri("/storefront/promotions/verify")
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .body(new PromotionVerifyVm(couponCode, subtotal, productIds))
                .retrieve()
                .body(PromotionVerifyResultVm.class);
        } catch (HttpClientErrorException ex) {
            throw refusedOrUnavailable(ex, "verify");
        } catch (RestClientException ex) {
            log.error("Promotion service unavailable while verifying a coupon: {}", ex.getMessage());
            throw new InternalServerErrorException(UNAVAILABLE);
        }
        return toCoupon(result, productIds);
    }

    /**
     * Takes one use of the coupon for this order attempt. A 400 (used up, expired meanwhile) means nothing was taken
     * and is answered with promotion's code; any other failure is a 500 and the usage stays "may hold a use", so the
     * caller releases it.
     */
    public void applyUsage(CouponUsage usage, Coupon coupon) {
        usage.sent();
        try {
            restClient.post()
                .uri(uri -> uri.path("/storefront/promotions/apply-usage")
                    .queryParam("reference", usage.reference()).build())
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .body(new PromotionApplyUsageVm(coupon.code(), coupon.eligibleProductIds()))
                .retrieve()
                .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            // a 4xx answer means promotion counted nothing (4-46)
            usage.refused();
            throw refusedOrUnavailable(ex, "apply-usage");
        } catch (RestClientException ex) {
            log.error("Promotion service unavailable while taking a coupon use: {}", ex.getMessage());
            throw new InternalServerErrorException(UNAVAILABLE);
        }
    }

    /** Compensation: gives back the use {@link #applyUsage} took under the reference. Best effort, logged. */
    public void releaseUsage(CouponUsage usage) {
        try {
            restClient.post()
                .uri(uri -> uri.path("/storefront/promotions/release-usage")
                    .queryParam("reference", usage.reference()).build())
                .headers(headers -> headers.setBearerAuth(AuthenticationUtils.extractJwt()))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientException ex) {
            log.error("Could not give back the coupon use of failed order attempt {}: {}", usage.reference(),
                ex.getMessage());
        }
    }

    /**
     * Gives back the coupon use of a placed order under its stored reference, through promotion's internal endpoint
     * with the order service's own token (decisions.md 5-4, 5-5); promotion releases a use at most once.
     *
     * @return whether promotion confirmed (2xx); false when it is down or refused, so the caller retries later
     */
    public boolean releaseOrderUsage(Long orderId, UUID reference) {
        String what = "release the coupon use of order " + orderId;
        return InternalCalls.send(serviceTokens, what, token -> restClient.post()
            .uri(uri -> uri.path("/internal/promotions/release-usage").queryParam("reference", reference).build())
            .headers(headers -> headers.setBearerAuth(token))
            .retrieve()
            .toBodilessEntity());
    }

    private static Coupon toCoupon(PromotionVerifyResultVm result, List<Long> productIds) {
        Coupon.Type type = result == null ? null : typeOf(result.discountType());
        if (result == null || type == null || result.discountValue() == null || result.couponCode() == null) {
            log.error("Promotion answered an unreadable coupon: {}", result);
            throw new InternalServerErrorException(UNAVAILABLE);
        }
        // only products of this checkout, in its order (promotion answers a subset of the request)
        List<Long> eligible = result.productIds() == null ? List.of()
            : productIds.stream().filter(result.productIds()::contains).toList();
        if (eligible.isEmpty()) {
            throw new CouponRejectedException(NOT_APPLICABLE,
                "This coupon does not apply to the products of this order");
        }
        return new Coupon(result.couponCode(), type, result.discountValue(), eligible);
    }

    private static Coupon.Type typeOf(String discountType) {
        try {
            return discountType == null ? null : Coupon.Type.valueOf(discountType);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Promotion's 400 with its code; a 400 without one (request validation) reads as an unknown coupon. */
    private static RuntimeException refusedOrUnavailable(HttpClientErrorException ex, String call) {
        if (!ex.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
            log.error("Promotion refused {} with {}: {}", call, ex.getStatusCode(), ex.getMessage());
            return new InternalServerErrorException(UNAVAILABLE);
        }
        RemoteErrorVm error = errorBody(ex);
        if (error == null || error.title() == null || !ERROR_CODE.matcher(error.title()).matches()) {
            return new CouponRejectedException(NOT_FOUND, INVALID_CODE);
        }
        String detail = error.detail() == null || error.detail().isBlank() ? INVALID_CODE : error.detail();
        return new CouponRejectedException(error.title(), detail);
    }

    private static RemoteErrorVm errorBody(HttpClientErrorException ex) {
        try {
            return ex.getResponseBodyAs(RemoteErrorVm.class);
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }
}
