package com.shopapp.recommendation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.recommendation.service.ProductVectorSyncService;
import com.shopapp.recommendation.support.AbstractRecommendationIT;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SimilarityIT extends AbstractRecommendationIT {

    @Autowired
    private ProductVectorSyncService syncService;

    @Autowired
    private ObjectMapper mapper;

    private void catalog() {
        phone(1, "Galaxy S24", "Samsung", 1);
        phone(2, "Galaxy A55", "Samsung", 1);
        phone(3, "iPhone 16", "Apple", 1);
        PRODUCT.product(4, "USB-C Cable", "Anker", List.of("Accessories"), "Braided charging cable, 1 m", at(1));
        PRODUCT.product(5, "Coffee Grinder", "Bosch", List.of("Kitchen"), "Electric burr grinder for coffee beans",
            at(1));
        for (long id = 1; id <= 5; id++) {
            syncService.sync(id, null);
        }
    }

    private List<Long> similarIds(long id) throws Exception {
        String body = mockMvc.perform(get("/embedding/product/{id}/similarity", id))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode list = mapper.readTree(body);
        return list.valueStream().map(node -> node.get("id").asLong()).toList();
    }

    @Test
    void similarity_phonesFirst_thenUnrelatedProducts_withoutTheProductItself() throws Exception {
        catalog();

        List<Long> ids = similarIds(1);

        assertThat(ids).doesNotContain(1L).hasSize(4);
        assertThat(ids.subList(0, 2)).containsExactlyInAnyOrder(2L, 3L);
        assertThat(ids.getFirst()).isEqualTo(2L); // the other Samsung phone is nearest
    }

    @Test
    void similarity_isPublic_andReturnsCardFields() throws Exception {
        catalog();

        mockMvc.perform(get("/embedding/product/1/similarity"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(2))
            .andExpect(jsonPath("$[0].name").value("Galaxy A55"))
            .andExpect(jsonPath("$[0].slug").value("galaxy-a55"))
            .andExpect(jsonPath("$[0].price").value(102.0))
            .andExpect(jsonPath("$[0].brandName").value("Samsung"))
            .andExpect(jsonPath("$[0].thumbnailUrl").value("http://media/2.png"));
    }

    @Test
    void unpublishedProduct_isNeverRecommended_andGetsNoRecommendations() throws Exception {
        catalog();
        PRODUCT.remove(2);
        syncService.sync(2, null);

        assertThat(similarIds(1)).doesNotContain(2L).hasSize(3);
        assertThat(similarIds(2)).isEmpty();
    }

    @Test
    void unknownProduct_emptyList() throws Exception {
        catalog();

        assertThat(similarIds(999)).isEmpty();
    }

    @Test
    void resultIsBoundedToTopK() throws Exception {
        for (long id = 1; id <= 12; id++) {
            phone(id, "Phone " + id, "Brand" + id, 1);
            syncService.sync(id, null);
        }

        assertThat(similarIds(1)).hasSize(8).doesNotContain(1L);
    }

    @Test
    void nonNumericId_badRequest() throws Exception {
        mockMvc.perform(get("/embedding/product/abc/similarity")).andExpect(status().isBadRequest());
    }

    @Test
    void reindex_adminOnly_embedsEveryProduct() throws Exception {
        phone(1, "Galaxy S24", "Samsung", 1);
        phone(2, "Galaxy A55", "Samsung", 1);

        mockMvc.perform(post("/backoffice/reindex")).andExpect(status().isForbidden());
        mockMvc.perform(post("/backoffice/reindex").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(post("/backoffice/reindex").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.indexed").value(2))
            .andExpect(jsonPath("$.removed").value(0));

        assertThat(embedded(1)).isTrue();
        assertThat(embedded(2)).isTrue();
    }
}
