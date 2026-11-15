package com.shopapp.rating.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.rating.support.AbstractRatingIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Back office: list with filters, latest ratings, delete; ADMIN only. */
class RatingAdminIT extends AbstractRatingIT {

    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private long phoneByLan;

    @BeforeEach
    void ratings() {
        phoneByLan = insertRating(10, "Galaxy S24", 5, "Great camera", "c-1", "Lan", "Nguyen",
            NOW.minus(2, ChronoUnit.DAYS));
        insertRating(11, "USB-C Cable", 2, "Broke after a week", "c-1", "Lan", "Nguyen",
            NOW.minus(1, ChronoUnit.DAYS));
        insertRating(10, "Galaxy S24", 3, "Battery is average", "c-2", "Minh", "Tran", NOW);
    }

    @Test
    void list_newestFirst_withPaging() throws Exception {
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("pageSize", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ratingList[*].content", contains("Battery is average", "Broke after a week")))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void list_filters_ignoringCase() throws Exception {
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("productName", "galaxy"))
            .andExpect(jsonPath("$.ratingList[*].content", contains("Battery is average", "Great camera")));
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("customerName", "lan nguy"))
            .andExpect(jsonPath("$.ratingList[*].content", contains("Broke after a week", "Great camera")));
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("message", "CAMERA"))
            .andExpect(jsonPath("$.ratingList[*].content", contains("Great camera")));
        mockMvc.perform(get("/backoffice/ratings").with(admin())
                .param("createdFrom", "2026-11-08T23:00:00Z").param("createdTo", "2026-11-09T23:00:00Z"))
            .andExpect(jsonPath("$.ratingList[*].content", contains("Broke after a week")));
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("productName", "galaxy")
                .param("customerName", "minh"))
            .andExpect(jsonPath("$.ratingList[*].content", contains("Battery is average")));
    }

    @Test
    void list_likeWildcardsInFiltersAreLiteral() throws Exception {
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("message", "%"))
            .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("productName", "_"))
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void list_boundedParameters() throws Exception {
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("pageSize", "101"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("pageNo", "-1"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("message", "x".repeat(256)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/ratings").with(admin()).param("createdFrom", "yesterday"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void latest_newestFirst_countBounded() throws Exception {
        mockMvc.perform(get("/backoffice/ratings/latest/2").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].content", contains("Battery is average", "Broke after a week")))
            .andExpect(jsonPath("$[0].productName").value("Galaxy S24"))
            .andExpect(jsonPath("$[0].star").value(3));
        mockMvc.perform(get("/backoffice/ratings/latest/0").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/ratings/latest/51").with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/ratings/latest/50").with(admin()))
            .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void delete_anyRating_thenNotFound() throws Exception {
        mockMvc.perform(delete("/backoffice/ratings/{id}", phoneByLan).with(admin()))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/backoffice/ratings/{id}", phoneByLan).with(admin()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Rating " + phoneByLan + " is not found"));
        assertThat(ratingCount()).isEqualTo(2);
    }

    @Test
    void customer_403_anonymous_401() throws Exception {
        mockMvc.perform(get("/backoffice/ratings").with(customer("c-1"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/ratings/latest/5").with(customer("c-1"))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/backoffice/ratings/{id}", phoneByLan).with(customer("c-1")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/ratings")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/backoffice/ratings/{id}", phoneByLan).with(csrf()))
            .andExpect(status().isUnauthorized());
        assertThat(ratingCount()).isEqualTo(3);
    }
}
