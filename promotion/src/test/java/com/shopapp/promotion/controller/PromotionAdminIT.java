package com.shopapp.promotion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.promotion.support.AbstractPromotionIT;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class PromotionAdminIT extends AbstractPromotionIT {

    private ResultActions postJson(String json) throws Exception {
        return mockMvc.perform(post("/backoffice/promotions").with(admin())
            .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String putJson(long id, String createJson) {
        return "{\"id\":" + id + "," + createJson.substring(createJson.indexOf('{') + 1);
    }

    @Test
    void createsGetsAndListsAPromotion() throws Exception {
        String json = promotionJson("SAVE10").replace("\"productIds\":[1]", "\"productIds\":[3,1,3]")
            .replace("\"minimumOrderPurchaseAmount\":0", "\"minimumOrderPurchaseAmount\":49.99");
        postJson(json)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.couponCode").value("SAVE10"))
            .andExpect(jsonPath("$.discountType").value("PERCENTAGE"))
            .andExpect(jsonPath("$.discountPercentage").value(10))
            .andExpect(jsonPath("$.discountAmount").value(nullValue()))
            .andExpect(jsonPath("$.minimumOrderPurchaseAmount").value(49.99))
            .andExpect(jsonPath("$.isActive").value(true))
            .andExpect(jsonPath("$.usageCount").value(0))
            .andExpect(jsonPath("$.usageLimit").value(nullValue()))
            .andExpect(jsonPath("$.productIds", contains(1, 3)))
            .andExpect(jsonPath("$.brandIds", hasSize(0)));
        long id = jdbc.queryForObject("select id from promotion", Long.class);
        assertThat(jdbc.queryForObject("select created_by from promotion", String.class)).isEqualTo("admin-user");

        mockMvc.perform(get("/backoffice/promotions/{id}", id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Promo SAVE10"))
            .andExpect(jsonPath("$.applyTo").value("PRODUCT"))
            .andExpect(jsonPath("$.productIds", contains(1, 3)));

        mockMvc.perform(get("/backoffice/promotions").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.promotionDetailVmList", hasSize(1)))
            .andExpect(jsonPath("$.promotionDetailVmList[0].couponCode").value("SAVE10"))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void filtersAndPagesTheList() throws Exception {
        createPromotion(promotionJson("SUMMER"));
        createPromotion(promotionJson("WINTER").replace("Promo WINTER", "Winter sale"));
        String future = promotionJson("FUTURE", daysFromNow(20), daysFromNow(30));
        createPromotion(future);

        mockMvc.perform(get("/backoffice/promotions").param("promotionName", "winter").with(admin()))
            .andExpect(jsonPath("$.promotionDetailVmList[*].couponCode", contains("WINTER")));
        mockMvc.perform(get("/backoffice/promotions").param("couponCode", "umm").with(admin()))
            .andExpect(jsonPath("$.promotionDetailVmList[*].couponCode", contains("SUMMER")));
        mockMvc.perform(get("/backoffice/promotions").param("couponCode", "%").with(admin()))
            .andExpect(jsonPath("$.promotionDetailVmList", hasSize(0)));
        mockMvc.perform(get("/backoffice/promotions").param("startDate", daysFromNow(15).toString()).with(admin()))
            .andExpect(jsonPath("$.promotionDetailVmList[*].couponCode", contains("FUTURE")));
        mockMvc.perform(get("/backoffice/promotions").param("endDate", daysFromNow(5).toString()).with(admin()))
            .andExpect(jsonPath("$.promotionDetailVmList", hasSize(2)));
        mockMvc.perform(get("/backoffice/promotions").param("pageSize", "2").param("pageNo", "1").with(admin()))
            .andExpect(jsonPath("$.promotionDetailVmList[*].couponCode", contains("SUMMER")))
            .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/backoffice/promotions").param("pageSize", "101").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/promotions").param("pageNo", "-1").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void updatesAllFieldsAndKeepsTheUsageCount() throws Exception {
        long id = createPromotion(promotionJson("EDITME"));
        jdbc.update("update promotion set usage_count = 2 where id = ?", id);
        String updated = promotionJson("EDITED")
            .replace("\"discountType\":\"PERCENTAGE\"", "\"discountType\":\"FIXED\"")
            .replace("\"discountAmount\":null", "\"discountAmount\":15.50")
            .replace("\"usageType\":\"UNLIMITED\",\"usageLimit\":null", "\"usageType\":\"LIMITED\",\"usageLimit\":5")
            .replace("\"isActive\":true", "\"isActive\":false")
            .replace("\"applyTo\":\"PRODUCT\"", "\"applyTo\":\"BRAND\"")
            .replace("\"brandIds\":[]", "\"brandIds\":[7]");

        mockMvc.perform(put("/backoffice/promotions").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(putJson(id, updated)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.couponCode").value("EDITED"))
            .andExpect(jsonPath("$.discountType").value("FIXED"))
            .andExpect(jsonPath("$.discountAmount").value(15.5))
            .andExpect(jsonPath("$.discountPercentage").value(nullValue()))
            .andExpect(jsonPath("$.usageLimit").value(5))
            .andExpect(jsonPath("$.usageCount").value(2))
            .andExpect(jsonPath("$.isActive").value(false))
            .andExpect(jsonPath("$.applyTo").value("BRAND"))
            .andExpect(jsonPath("$.brandIds", contains(7)))
            .andExpect(jsonPath("$.productIds", hasSize(0)));
        assertThat(jdbc.queryForObject("select count(*) from promotion_apply", Integer.class)).isEqualTo(1);

        String belowCount = updated.replace("\"usageLimit\":5", "\"usageLimit\":1");
        mockMvc.perform(put("/backoffice/promotions").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(putJson(id, belowCount)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("USAGE_LIMIT_BELOW_USAGE_COUNT"));
        mockMvc.perform(put("/backoffice/promotions").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(putJson(999, updated)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletesAnUnusedPromotionButNotAUsedOne() throws Exception {
        long unused = createPromotion(promotionJson("UNUSED"));
        long used = createPromotion(promotionJson("USED"));
        jdbc.update("insert into promotion_usage (promotion_id, reference, user_id) values (?, ?, 'u')", used,
            UUID.randomUUID());

        mockMvc.perform(delete("/backoffice/promotions/{id}", unused).with(admin()))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/backoffice/promotions/{id}", used).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("PROMOTION_IN_USE"));
        mockMvc.perform(delete("/backoffice/promotions/{id}", unused).with(admin()))
            .andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("select coupon_code from promotion", String.class)).containsExactly("USED");
        assertThat(jdbc.queryForObject("select count(*) from promotion_apply", Integer.class)).isEqualTo(1);
    }

    @Test
    void getUnknownIsNotFound() throws Exception {
        mockMvc.perform(get("/backoffice/promotions/{id}", 42).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Nested
    class Security {

        @Test
        void anonymousIsUnauthorized() throws Exception {
            mockMvc.perform(get("/backoffice/promotions")).andExpect(status().isUnauthorized());
            mockMvc.perform(post("/backoffice/promotions").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(promotionJson("ANON"))).andExpect(status().isUnauthorized());
        }

        @Test
        void customerIsForbidden() throws Exception {
            long id = createPromotion(promotionJson("ADMINONLY"));
            mockMvc.perform(get("/backoffice/promotions").with(customer())).andExpect(status().isForbidden());
            mockMvc.perform(get("/backoffice/promotions/{id}", id).with(customer()))
                .andExpect(status().isForbidden());
            mockMvc.perform(post("/backoffice/promotions").with(customer()).contentType(MediaType.APPLICATION_JSON)
                .content(promotionJson("MINE"))).andExpect(status().isForbidden());
            mockMvc.perform(put("/backoffice/promotions").with(customer()).contentType(MediaType.APPLICATION_JSON)
                .content(putJson(id, promotionJson("MINE")))).andExpect(status().isForbidden());
            mockMvc.perform(delete("/backoffice/promotions/{id}", id).with(customer()))
                .andExpect(status().isForbidden());
            assertThat(jdbc.queryForList("select coupon_code from promotion", String.class))
                .containsExactly("ADMINONLY");
        }
    }

    @Nested
    class Validation {

        @Test
        void endDateMustBeAfterStartDate() throws Exception {
            String json = promotionJson("DATES", daysFromNow(-1), daysFromNow(-2));
            postJson(json).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasItem("dateRangeValid end date must be after start date")));
        }

        @Test
        void percentageMustBeAboveZeroAndAtMostHundred() throws Exception {
            postJson(promotionJson("ZERO").replace("\"discountPercentage\":10", "\"discountPercentage\":0"))
                .andExpect(status().isBadRequest());
            postJson(promotionJson("OVER").replace("\"discountPercentage\":10", "\"discountPercentage\":100.01"))
                .andExpect(status().isBadRequest());
            postJson(promotionJson("NOPCT").replace("\"discountPercentage\":10", "\"discountPercentage\":null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0]").value(
                    "discountValid discount value is required for the discount type"));
            postJson(promotionJson("FULL").replace("\"discountPercentage\":10", "\"discountPercentage\":100"))
                .andExpect(status().isCreated());
        }

        @Test
        void fixedNeedsAPositiveAmount() throws Exception {
            String fixed = promotionJson("FIXED").replace("\"discountType\":\"PERCENTAGE\"", "\"discountType\":\"FIXED\"");
            postJson(fixed).andExpect(status().isBadRequest());
            postJson(fixed.replace("\"discountAmount\":null", "\"discountAmount\":-1"))
                .andExpect(status().isBadRequest());
            postJson(fixed.replace("\"discountAmount\":null", "\"discountAmount\":5"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.discountPercentage").value(nullValue()));
        }

        @Test
        void limitedNeedsAUsageLimitAndTargetsMustMatchApplyTo() throws Exception {
            postJson(promotionJson("LIMITED").replace("\"usageType\":\"UNLIMITED\"", "\"usageType\":\"LIMITED\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0]").value(
                    "usageLimitValid a limited promotion needs a usage limit of at least 1"));
            postJson(promotionJson("NOBRAND").replace("\"applyTo\":\"PRODUCT\"", "\"applyTo\":\"BRAND\""))
                .andExpect(status().isBadRequest());
            postJson(promotionJson("NAMELESS").replace("\"name\":\"Promo NAMELESS\"", "\"name\":\" \""))
                .andExpect(status().isBadRequest());
            postJson(promotionJson("BAD CODE")).andExpect(status().isBadRequest());
            assertThat(jdbc.queryForObject("select count(*) from promotion", Integer.class)).isZero();
        }

        @Test
        void couponCodeAndSlugAreUniqueCaseInsensitively() throws Exception {
            long first = createPromotion(promotionJson("Unique"));
            postJson(promotionJson("UNIQUE").replace("promo-unique", "other-slug"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("COUPON_CODE_ALREADY_EXISTED"));
            postJson(promotionJson("OTHER").replace("promo-other", "promo-unique"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("SLUG_ALREADY_EXISTED"));
            long second = createPromotion(promotionJson("SECOND"));
            mockMvc.perform(put("/backoffice/promotions").with(admin()).contentType(MediaType.APPLICATION_JSON)
                    .content(putJson(second, promotionJson("unique").replace("promo-unique", "promo-second"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("COUPON_CODE_ALREADY_EXISTED"));
            mockMvc.perform(put("/backoffice/promotions").with(admin()).contentType(MediaType.APPLICATION_JSON)
                    .content(putJson(first, promotionJson("Unique"))))
                .andExpect(status().isOk());
        }
    }
}
