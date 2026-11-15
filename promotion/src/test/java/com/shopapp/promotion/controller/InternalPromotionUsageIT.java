package com.shopapp.promotion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.promotion.support.AbstractPromotionIT;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Internal release-usage (decisions.md 5-5): the order service gives a cancelled order's coupon use back with its own
 * client-credentials token (realm role SERVICE), whoever took the use; once per reference.
 */
class InternalPromotionUsageIT extends AbstractPromotionIT {

    private static final String INTERNAL_RELEASE = "/internal/promotions/release-usage";

    private static RequestPostProcessor service() {
        return jwt().jwt(token -> token.subject("service-account-order-service"))
            .authorities(new SimpleGrantedAuthority("ROLE_SERVICE"));
    }

    private static String limited(String code, int limit) {
        return promotionJson(code).replace("\"usageType\":\"UNLIMITED\",\"usageLimit\":null",
            "\"usageType\":\"LIMITED\",\"usageLimit\":" + limit);
    }

    private void apply(String code, UUID reference, String customer) throws Exception {
        mockMvc.perform(post("/storefront/promotions/apply-usage").param("reference", reference.toString())
                .with(customer(customer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"couponCode\":\"" + code + "\",\"productIds\":[1],\"orderId\":null}"))
            .andExpect(status().isOk());
    }

    private ResultActions release(UUID reference, RequestPostProcessor caller) throws Exception {
        return mockMvc.perform(post(INTERNAL_RELEASE).param("reference", reference.toString()).with(caller));
    }

    @Test
    void serviceReleasesAnyCustomersUse_once() throws Exception {
        long id = createPromotion(limited("BACK", 1));
        UUID reference = UUID.randomUUID();
        apply("BACK", reference, "customer-1");
        assertThat(usageCount(id)).isEqualTo(1);

        release(reference, service()).andExpect(status().isNoContent());
        assertThat(usageCount(id)).isZero();

        // again (a retried cancel), or the customer-bound release: nothing more
        release(reference, service()).andExpect(status().isNoContent());
        mockMvc.perform(post("/storefront/promotions/release-usage").param("reference", reference.toString())
            .with(customer("customer-1"))).andExpect(status().isNoContent());
        assertThat(usageCount(id)).isZero();
        // the use is free again for another customer
        apply("BACK", UUID.randomUUID(), "customer-2");
        assertThat(usageCount(id)).isEqualTo(1);
    }

    @Test
    void concurrentReleases_giveBackOnce() throws Exception {
        long id = createPromotion(limited("RACE", 5));
        UUID reference = UUID.randomUUID();
        apply("RACE", reference, "customer-1");
        apply("RACE", UUID.randomUUID(), "customer-2");

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(() -> release(reference, service()).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isEqualTo(204);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(usageCount(id)).isEqualTo(1);
    }

    @Test
    void unknownReference_isNoOp_andTheReferenceIsRequired() throws Exception {
        release(UUID.randomUUID(), service()).andExpect(status().isNoContent());
        mockMvc.perform(post(INTERNAL_RELEASE).with(service())).andExpect(status().isBadRequest());
        mockMvc.perform(post(INTERNAL_RELEASE).param("reference", "not-a-uuid").with(service()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void customersAndAdmins_areRefused() throws Exception {
        long id = createPromotion(limited("MINE", 1));
        UUID reference = UUID.randomUUID();
        apply("MINE", reference, "customer-1");

        release(reference, customer("customer-1")).andExpect(status().isForbidden());
        release(reference, admin()).andExpect(status().isForbidden());
        release(reference, csrf()).andExpect(status().isUnauthorized());
        assertThat(usageCount(id)).isEqualTo(1);
    }
}
