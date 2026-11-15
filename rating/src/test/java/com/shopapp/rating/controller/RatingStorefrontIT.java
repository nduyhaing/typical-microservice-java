package com.shopapp.rating.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.rating.support.AbstractRatingIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Storefront: post a rating (buyers only, one per product), public list and average, own delete. */
class RatingStorefrontIT extends AbstractRatingIT {

    @BeforeEach
    void catalog() {
        STUB.product(10, "Galaxy S24");
        STUB.product(11, "USB-C Cable");
    }

    private ResultActions postRating(RequestPostProcessor who, String body) throws Exception {
        return mockMvc.perform(post("/storefront/ratings").with(who).contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    @Test
    void buyer_rates_201_withServerProductNameAndNameFromToken() throws Exception {
        STUB.bought("c-1", 10);

        postRating(customer("c-1", "Lan", "Nguyen"), ratingJson(10, 4, "Great phone"))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.star").value(4))
            .andExpect(jsonPath("$.content").value("Great phone"))
            .andExpect(jsonPath("$.productId").value(10))
            // never the client's product name
            .andExpect(jsonPath("$.productName").value("Galaxy S24"))
            .andExpect(jsonPath("$.firstName").value("Lan"))
            .andExpect(jsonPath("$.lastName").value("Nguyen"))
            .andExpect(jsonPath("$.createdBy").value("c-1"))
            .andExpect(jsonPath("$.createdOn").isNotEmpty());

        assertThat(ratingCount()).isEqualTo(1);
        // order asked with the customer's own token
        assertThat(STUB.requests()).filteredOn(r -> r.startsWith("GET /order")).singleElement()
            .isEqualTo("GET /order/storefront/orders/completed?productId=10 | Bearer token-c-1");
    }

    @Test
    void tokenWithoutNameClaims_usesTheUsername() throws Exception {
        STUB.bought("c-1", 10);

        postRating(customer("c-1", null, null), ratingJson(10, 5, "Nice"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.firstName").value("c-1"))
            .andExpect(jsonPath("$.lastName").value(""));
    }

    @Test
    void notBought_400_ratingNotAllowed_nothingSaved() throws Exception {
        STUB.bought("c-2", 10);

        postRating(customer("c-1"), ratingJson(10, 4, "Never bought it"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Only customers who bought this product can review it"));
        assertThat(ratingCount()).isZero();
    }

    @Test
    void orderDown_500_nothingSaved() throws Exception {
        STUB.bought("c-1", 10);
        STUB.orderDown();

        postRating(customer("c-1"), ratingJson(10, 4, "ok"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Order service is unavailable, please try again later"));
        assertThat(ratingCount()).isZero();
    }

    @Test
    void productDown_500_nothingSaved() throws Exception {
        STUB.bought("c-1", 10);
        STUB.productDown();

        postRating(customer("c-1"), ratingJson(10, 4, "ok"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value("Product service is unavailable, please try again later"));
        assertThat(ratingCount()).isZero();
    }

    @Test
    void boughtButProductNoLongerShown_404() throws Exception {
        STUB.bought("c-1", 12);

        postRating(customer("c-1"), ratingJson(12, 4, "ok"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Product 12 is not found"));
        assertThat(ratingCount()).isZero();
    }

    @Test
    void secondRatingOfTheSameProduct_409_otherCustomerAndOtherProductAreFine() throws Exception {
        STUB.bought("c-1", 10);
        STUB.bought("c-1", 11);
        STUB.bought("c-2", 10);
        postRating(customer("c-1"), ratingJson(10, 4, "first")).andExpect(status().isCreated());

        postRating(customer("c-1"), ratingJson(10, 2, "second"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.detail").value("You have already reviewed this product"));
        postRating(customer("c-1"), ratingJson(11, 5, "cable")).andExpect(status().isCreated());
        postRating(customer("c-2"), ratingJson(10, 3, "mine")).andExpect(status().isCreated());
        assertThat(ratingCount()).isEqualTo(3);
    }

    @Test
    void invalidStarOrContentOrProduct_400_beforeAnyRemoteCall() throws Exception {
        STUB.bought("c-1", 10);

        postRating(customer("c-1"), ratingJson(10, 0, "zero")).andExpect(status().isBadRequest());
        postRating(customer("c-1"), ratingJson(10, 6, "six")).andExpect(status().isBadRequest());
        postRating(customer("c-1"), ratingJson(10, 4, "x".repeat(1001))).andExpect(status().isBadRequest());
        postRating(customer("c-1"), ratingJson(10, 4, "   ")).andExpect(status().isBadRequest());
        postRating(customer("c-1"), ratingJson(10, 4, null)).andExpect(status().isBadRequest());
        postRating(customer("c-1"), "{\"star\":4,\"content\":\"no product\"}").andExpect(status().isBadRequest());
        postRating(customer("c-1"), ratingJson(-1, 4, "negative")).andExpect(status().isBadRequest());

        assertThat(ratingCount()).isZero();
        assertThat(STUB.requests()).isEmpty();
        // 1000 characters is fine
        postRating(customer("c-1"), ratingJson(10, 4, "x".repeat(1000))).andExpect(status().isCreated());
    }

    @Test
    void anonymous_401_adminIsNotACustomer_403() throws Exception {
        STUB.bought("c-1", 10);

        mockMvc.perform(post("/storefront/ratings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(ratingJson(10, 4, "anon")))
            .andExpect(status().isUnauthorized());
        postRating(admin(), ratingJson(10, 4, "admin")).andExpect(status().isForbidden());
        assertThat(ratingCount()).isZero();
    }

    @Test
    void publicList_newestFirst_paged_onlyThatProduct() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        insertRating(10, "Galaxy S24", 3, "oldest", "c-1", "A", "One", now.minus(3, ChronoUnit.DAYS));
        insertRating(10, "Galaxy S24", 5, "newest", "c-2", "B", "Two", now.minus(1, ChronoUnit.DAYS));
        insertRating(10, "Galaxy S24", 4, "middle", "c-3", "C", "Three", now.minus(2, ChronoUnit.DAYS));
        insertRating(11, "USB-C Cable", 1, "other product", "c-1", "A", "One", now);

        mockMvc.perform(get("/storefront/ratings/products/10").param("pageNo", "0").param("pageSize", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ratingList[*].content", contains("newest", "middle")))
            .andExpect(jsonPath("$.ratingList[0].firstName").value("B"))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2));
        mockMvc.perform(get("/storefront/ratings/products/10").param("pageNo", "1").param("pageSize", "2"))
            .andExpect(jsonPath("$.ratingList[*].content", contains("oldest")));
        mockMvc.perform(get("/storefront/ratings/products/99"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void publicList_pagingIsBounded() throws Exception {
        mockMvc.perform(get("/storefront/ratings/products/10").param("pageSize", "51"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/ratings/products/10").param("pageSize", "0"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/ratings/products/10").param("pageNo", "-1"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/ratings/products/10").param("pageNo", "10000001"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/storefront/ratings/products/10").param("pageSize", "50"))
            .andExpect(status().isOk());
    }

    @Test
    void averageStar_zeroWithoutRatings_thenTheMean() throws Exception {
        mockMvc.perform(get("/storefront/ratings/product/10/average-star"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(0.0));

        Instant now = Instant.now();
        insertRating(10, "Galaxy S24", 4, "a", "c-1", "A", "One", now);
        insertRating(10, "Galaxy S24", 5, "b", "c-2", "B", "Two", now);
        insertRating(11, "USB-C Cable", 1, "c", "c-1", "A", "One", now);

        mockMvc.perform(get("/storefront/ratings/product/10/average-star"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value(4.5));
    }

    @Test
    void customer_deletesOwnRating_notSomeoneElses() throws Exception {
        Instant now = Instant.now();
        long mine = insertRating(10, "Galaxy S24", 4, "mine", "c-1", "A", "One", now);
        long theirs = insertRating(10, "Galaxy S24", 2, "theirs", "c-2", "B", "Two", now);

        mockMvc.perform(delete("/storefront/ratings/{id}", theirs).with(customer("c-1")))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/storefront/ratings/{id}", mine).with(customer("c-1")))
            .andExpect(status().isNoContent());
        mockMvc.perform(delete("/storefront/ratings/{id}", mine).with(customer("c-1")))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/storefront/ratings/{id}", theirs).with(csrf()))
            .andExpect(status().isUnauthorized());

        assertThat(jdbc.queryForList("select content from rating", String.class)).containsExactly("theirs");
    }

    @Test
    void afterDeletingOwnRating_theCustomerMayRateAgain() throws Exception {
        STUB.bought("c-1", 10);
        long id = insertRating(10, "Galaxy S24", 1, "first", "c-1", "A", "One", Instant.now());
        mockMvc.perform(delete("/storefront/ratings/{id}", id).with(customer("c-1")))
            .andExpect(status().isNoContent());

        postRating(customer("c-1"), ratingJson(10, 5, "changed my mind")).andExpect(status().isCreated());
    }
}
