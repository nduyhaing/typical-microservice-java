package com.shopapp.promotion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.promotion.support.AbstractPromotionIT;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class PromotionUsageIT extends AbstractPromotionIT {

    private static String limited(String code, int limit) {
        return promotionJson(code).replace("\"usageType\":\"UNLIMITED\",\"usageLimit\":null",
            "\"usageType\":\"LIMITED\",\"usageLimit\":" + limit);
    }

    private ResultActions apply(String code, UUID reference, RequestPostProcessor user) throws Exception {
        return mockMvc.perform(post("/storefront/promotions/apply-usage").param("reference", reference.toString())
            .with(user).contentType(MediaType.APPLICATION_JSON)
            .content("{\"couponCode\":\"" + code + "\",\"productIds\":[1,3],\"orderId\":null}"));
    }

    private ResultActions release(UUID reference, RequestPostProcessor user) throws Exception {
        return mockMvc.perform(post("/storefront/promotions/release-usage").param("reference", reference.toString())
            .with(user));
    }

    @Test
    void applyTakesOneUseAndRecordsIt() throws Exception {
        long id = createPromotion(promotionJson("SAVE10"));
        UUID reference = UUID.randomUUID();

        apply("save10", reference, customer()).andExpect(status().isOk())
            .andExpect(jsonPath("$.promotionId").value(id))
            .andExpect(jsonPath("$.couponCode").value("SAVE10"))
            .andExpect(jsonPath("$.reference").value(reference.toString()));

        assertThat(usageCount(id)).isEqualTo(1);
        assertThat(jdbc.queryForMap("select user_id, product_id, released_on from promotion_usage where reference = ?",
            reference)).containsEntry("user_id", "customer-1").containsEntry("product_id", 1L)
            .containsEntry("released_on", null);

        apply("SAVE10", reference, customer()).andExpect(status().isOk());
        assertThat(usageCount(id)).as("same reference is idempotent").isEqualTo(1);
        apply("SAVE10", reference, customer("customer-2")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("USAGE_REFERENCE_CONFLICT"));
        assertThat(usageCount(id)).isEqualTo(1);
    }

    @Test
    void limitedCouponRefusesTheUseAfterTheLimit() throws Exception {
        long id = createPromotion(limited("ONCE", 1));
        apply("ONCE", UUID.randomUUID(), customer()).andExpect(status().isOk());
        apply("ONCE", UUID.randomUUID(), customer("customer-2")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("PROMOTION_USED_UP"));
        assertThat(usageCount(id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from promotion_usage", Integer.class)).isEqualTo(1);
    }

    @Test
    void applyChecksActiveAndDates() throws Exception {
        createPromotion(promotionJson("OFF").replace("\"isActive\":true", "\"isActive\":false"));
        createPromotion(promotionJson("OVER", daysFromNow(-5), daysFromNow(-1)));
        apply("OFF", UUID.randomUUID(), customer()).andExpect(jsonPath("$.title").value("PROMOTION_NOT_FOUND"));
        apply("OVER", UUID.randomUUID(), customer()).andExpect(jsonPath("$.title").value("PROMOTION_EXPIRED"));
        apply("NOPE", UUID.randomUUID(), customer()).andExpect(jsonPath("$.title").value("PROMOTION_NOT_FOUND"));
        assertThat(jdbc.queryForObject("select sum(usage_count) from promotion", Integer.class)).isZero();
    }

    @Test
    void concurrentAppliesNeverExceedTheLimit() throws Exception {
        int limit = 3;
        int callers = 12;
        long id = createPromotion(limited("RUSH", limit));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                String user = "customer-" + i;
                Callable<Integer> call = () -> {
                    start.await();
                    return apply("RUSH", UUID.randomUUID(), customer(user)).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(s -> s == 200).hasSize(limit);
            assertThat(statuses).filteredOn(s -> s == 400).hasSize(callers - limit);
        } finally {
            pool.shutdownNow();
        }
        assertThat(usageCount(id)).isEqualTo(limit);
        assertThat(jdbc.queryForObject("select count(*) from promotion_usage", Integer.class)).isEqualTo(limit);
    }

    @Test
    void releaseGivesTheUseBackOnceAndOnlyForItsOwner() throws Exception {
        long id = createPromotion(limited("BACK", 1));
        UUID reference = UUID.randomUUID();
        apply("BACK", reference, customer()).andExpect(status().isOk());

        release(reference, customer("customer-2")).andExpect(status().isNoContent());
        assertThat(usageCount(id)).as("another user's release is ignored").isEqualTo(1);

        release(reference, customer()).andExpect(status().isNoContent());
        assertThat(usageCount(id)).isZero();
        release(reference, customer()).andExpect(status().isNoContent());
        assertThat(usageCount(id)).as("released once").isZero();
        release(UUID.randomUUID(), customer()).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select released_on is not null from promotion_usage where reference = ?",
            Boolean.class, reference)).isTrue();

        apply("BACK", reference, customer()).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("USAGE_REFERENCE_CONFLICT"));
        apply("BACK", UUID.randomUUID(), customer("customer-2")).andExpect(status().isOk());
        assertThat(usageCount(id)).isEqualTo(1);
    }

    @Test
    void usageEndpointsNeedATokenAndAReference() throws Exception {
        createPromotion(promotionJson("AUTH"));
        mockMvc.perform(post("/storefront/promotions/apply-usage").param("reference", UUID.randomUUID().toString())
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"couponCode\":\"AUTH\"}"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/storefront/promotions/release-usage").param("reference", UUID.randomUUID().toString())
                .with(csrf()))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/storefront/promotions/apply-usage").with(customer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"couponCode\":\"AUTH\"}"))
            .andExpect(status().isBadRequest());
        // customers only (order relays the customer's token): an admin token is refused (decisions.md 4-75)
        apply("AUTH", UUID.randomUUID(), admin()).andExpect(status().isForbidden());
        release(UUID.randomUUID(), admin()).andExpect(status().isForbidden());
        mockMvc.perform(post("/storefront/promotions/apply-usage").param("reference", "not-a-uuid").with(customer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"couponCode\":\"AUTH\"}"))
            .andExpect(status().isBadRequest());
    }
}
