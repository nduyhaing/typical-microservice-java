package com.shopapp.promotion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.promotion.model.Promotion;
import com.shopapp.promotion.repository.PromotionRepository;
import com.shopapp.promotion.support.AbstractPromotionIT;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A request that passes the service's checks but loses a race to a concurrent one is stopped by the database's unique
 * index or check constraint: still a 400 with the rule's code, not a 500 (decisions.md 4-77). The race is simulated
 * by making the check miss what is already stored.
 */
class PromotionConstraintIT extends AbstractPromotionIT {

    @MockitoSpyBean
    private PromotionRepository promotionRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void duplicateCouponCodeAfterTheCheck_is400() throws Exception {
        createPromotion(promotionJson("RACE"));
        doReturn(false).when(promotionRepository).existsCouponCode(anyString(), any());

        mockMvc.perform(post("/backoffice/promotions").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(promotionJson("race").replace("\"slug\":\"promo-race\"", "\"slug\":\"other-slug\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("COUPON_CODE_ALREADY_EXISTED"));
    }

    @Test
    void duplicateSlugAfterTheCheck_is400() throws Exception {
        createPromotion(promotionJson("SLUG1"));
        doReturn(false).when(promotionRepository).existsSlug(anyString(), any());

        mockMvc.perform(post("/backoffice/promotions").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(promotionJson("SLUG2").replace("\"slug\":\"promo-slug2\"", "\"slug\":\"promo-slug1\"")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("SLUG_ALREADY_EXISTED"));
    }

    @Test
    void usageLimitBelowAUseTakenAfterTheCheck_is400() throws Exception {
        long id = createPromotion(promotionJson("USED").replace("\"usageType\":\"UNLIMITED\",\"usageLimit\":null",
            "\"usageType\":\"LIMITED\",\"usageLimit\":5"));
        jdbc.update("update promotion set usage_count = 3 where id = ?", id);
        // the service reads the promotion before the three uses were taken
        doAnswer(invocation -> {
            // the service's transaction-bound entity manager (the repository is an interface proxy: no real method)
            Promotion promotion = entityManager.find(Promotion.class, id);
            ReflectionTestUtils.setField(promotion, "usageCount", 0);
            return Optional.of(promotion);
        }).when(promotionRepository).findById(id);

        mockMvc.perform(put("/backoffice/promotions").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(promotionJson("USED").replace("\"usageType\":\"UNLIMITED\",\"usageLimit\":null",
                    "\"usageType\":\"LIMITED\",\"usageLimit\":1").replaceFirst("\\{", "{\"id\":" + id + ",")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("USAGE_LIMIT_BELOW_USAGE_COUNT"));
    }
}
