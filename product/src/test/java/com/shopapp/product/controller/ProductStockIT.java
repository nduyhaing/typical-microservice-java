package com.shopapp.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Product;
import com.shopapp.product.repository.ProductRepository;
import com.shopapp.product.service.ProductAdminService;
import com.shopapp.product.support.AbstractStorefrontIT;
import com.shopapp.product.viewmodel.product.ProductPutVm;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Stock quantity endpoints: update-quantity (inventory, ADMIN), subtract-quantity and restore-quantity (order, any
 * signed-in user), and the stock fields of the storefront view models.
 */
class ProductStockIT extends AbstractStorefrontIT {

    private static final String UPDATE = "/backoffice/products/update-quantity";
    private static final String SUBTRACT = "/backoffice/products/subtract-quantity";
    private static final String RESTORE = "/backoffice/products/restore-quantity";
    private static final String ADJUST = "/backoffice/products/adjust-quantity";

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductAdminService productAdminService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyAdjustments() {
        jdbc.execute("TRUNCATE stock_adjustment");
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer(String userId) {
        return jwt().jwt(token -> token.subject(userId)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private Product tracked(String slug, Long quantity) {
        Product product = fixtures.product(slug, slug, 10);
        product.setStockTrackingEnabled(true);
        return fixtures.withStock(fixtures.save(product), quantity);
    }

    private Product untracked(String slug, Long quantity) {
        Product product = fixtures.product(slug, slug, 10);
        product.setStockTrackingEnabled(false);
        return fixtures.withStock(fixtures.save(product), quantity);
    }

    /** Order always sends a reference (required since the 3c review). */
    private static MockHttpServletRequestBuilder subtract() {
        return put(SUBTRACT).param("reference", "r-" + UUID.randomUUID());
    }

    private static String deltas(Object... idDeltaPairs) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < idDeltaPairs.length; i += 2) {
            items.add("{\"productId\":" + idDeltaPairs[i] + ",\"delta\":" + idDeltaPairs[i + 1] + "}");
        }
        return "[" + String.join(",", items) + "]";
    }

    private long stockOf(Product product) {
        Long quantity = productRepository.findById(product.getId()).orElseThrow().getStockQuantity();
        return quantity == null ? 0 : quantity;
    }

    private static String items(Object... idQuantityPairs) {
        List<String> items = new ArrayList<>();
        for (int i = 0; i < idQuantityPairs.length; i += 2) {
            items.add("{\"productId\":" + idQuantityPairs[i] + ",\"quantity\":" + idQuantityPairs[i + 1] + "}");
        }
        return "[" + String.join(",", items) + "]";
    }

    // ---------- update-quantity ----------

    @Test
    void updateQuantity_setsStockQuantity_adminOnly() throws Exception {
        Product phone = tracked("phone", 1L);
        Product cable = tracked("cable", 2L);
        String body = "[{\"productId\":" + phone.getId() + ",\"stockQuantity\":8},"
            + "{\"productId\":" + cable.getId() + ",\"stockQuantity\":0}]";

        mockMvc.perform(put(UPDATE).with(customer("c-1")).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(put(UPDATE).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is4xxClientError());
        assertThat(stockOf(phone)).isEqualTo(1);

        mockMvc.perform(put(UPDATE).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(8);
        assertThat(stockOf(cable)).isZero();
    }

    @Test
    void updateQuantity_invalidBody_isBadRequest_unknownProduct_isNotFound() throws Exception {
        Product phone = tracked("phone", 1L);
        for (String body : List.of("[]", "[{\"productId\":" + phone.getId() + ",\"stockQuantity\":-1}]",
            "[{\"productId\":null,\"stockQuantity\":1}]", "[{\"productId\":" + phone.getId() + "}]",
            "[{\"productId\":" + phone.getId() + ",\"stockQuantity\":1},{\"productId\":" + phone.getId()
                + ",\"stockQuantity\":2}]")) {
            mockMvc.perform(put(UPDATE).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put(UPDATE).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"productId\":" + phone.getId() + ",\"stockQuantity\":5},"
                    + "{\"productId\":999999,\"stockQuantity\":5}]"))
            .andExpect(status().isNotFound());
        assertThat(stockOf(phone)).isEqualTo(1);
    }

    // ---------- adjust-quantity (inventory deltas, decisions.md 3c-60) ----------

    @Test
    void adjust_appliesDeltas_neverBelowZero_adminOnly() throws Exception {
        Product phone = tracked("phone", 5L);
        Product cable = tracked("cable", 1L);
        Product empty = tracked("empty", null);
        String body = deltas(phone.getId(), 3, cable.getId(), -4, empty.getId(), 2);

        mockMvc.perform(put(ADJUST).param("reference", "adj-1").with(customer("c-1"))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(put(ADJUST).param("reference", "adj-1").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is4xxClientError());
        assertThat(stockOf(phone)).isEqualTo(5);

        mockMvc.perform(put(ADJUST).param("reference", "adj-1").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(8);
        assertThat(stockOf(cable)).isZero();
        assertThat(stockOf(empty)).isEqualTo(2);
    }

    @Test
    void adjust_addsToTheCurrentStock_soUnitsSoldMeanwhileStaySold() throws Exception {
        Product phone = tracked("phone", 10L);
        mockMvc.perform(put(SUBTRACT).param("reference", "order-sold").with(customer("c-1"))
                .contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 2)))
            .andExpect(status().isNoContent());
        mockMvc.perform(put(ADJUST).param("reference", "adj-2").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(deltas(phone.getId(), 1)))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(9);
    }

    @Test
    void adjust_sameReferenceTwice_isAppliedOnce() throws Exception {
        Product phone = tracked("phone", 5L);
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(put(ADJUST).param("reference", "adj-3").with(admin())
                    .contentType(MediaType.APPLICATION_JSON).content(deltas(phone.getId(), 4)))
                .andExpect(status().isNoContent());
        }
        assertThat(stockOf(phone)).isEqualTo(9);
    }

    @Test
    void adjust_concurrentCalls_loseNoDelta_andARacingRetryIsAppliedOnce() throws Exception {
        Product phone = tracked("phone", 0L);
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                // two calls per reference: a retry racing the original
                String reference = "adj-c" + (i / 2);
                Callable<Integer> call = () -> {
                    start.await();
                    return mockMvc.perform(put(ADJUST).param("reference", reference).with(admin())
                            .contentType(MediaType.APPLICATION_JSON).content(deltas(phone.getId(), 1)))
                        .andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            for (Future<Integer> result : results) {
                assertThat(result.get()).isEqualTo(204);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(stockOf(phone)).isEqualTo(5);
    }

    @Test
    void adjust_invalidInput_changesNothing() throws Exception {
        Product phone = tracked("phone", 5L);
        for (String body : List.of("[]", "[{\"productId\":" + phone.getId() + "}]", deltas("null", 1),
            deltas(phone.getId(), 1, phone.getId(), 2), deltas(phone.getId(), 2_000_000_000))) {
            mockMvc.perform(put(ADJUST).param("reference", "adj-x").with(admin())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put(ADJUST).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(deltas(phone.getId(), 1)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(ADJUST).param("reference", "bad reference!").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(deltas(phone.getId(), 1)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(ADJUST).param("reference", "adj-y").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(deltas(phone.getId(), 1, 999999, 1)))
            .andExpect(status().isNotFound());
        assertThat(stockOf(phone)).isEqualTo(5);
        // the refused call did not use up its reference
        mockMvc.perform(put(ADJUST).param("reference", "adj-y").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(deltas(phone.getId(), 1)))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(6);
    }

    // ---------- back-office edit vs concurrent stock change (3c review I1) ----------

    @Test
    void adminUpdate_withAStaleStockInMemory_keepsTheConcurrentStockChange() {
        Product phone = tracked("phone", 5L);
        Long id = phone.getId();
        ProductPutVm edit = new ProductPutVm("phone renamed", "phone", 12.0, true, true, false, true, true, null,
            List.of(), null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            List.of(), null, null, List.of(), null);
        transactionTemplate.executeWithoutResult(status -> {
            // the admin's edit loads the product (stock 5) ...
            assertThat(productRepository.findById(id).orElseThrow().getStockQuantity()).isEqualTo(5);
            // ... an order takes 2 units meanwhile ...
            productRepository.subtractStock(id, 2);
            // ... and the edit is saved from the stale entity
            productAdminService.update(id, edit);
        });
        Product saved = productRepository.findById(id).orElseThrow();
        assertThat(saved.getName()).isEqualTo("phone renamed");
        assertThat(saved.getStockQuantity()).isEqualTo(3);
    }

    // ---------- subtract-quantity ----------

    @Test
    void subtract_decreasesTrackedProducts_leavesUntrackedAlone_forAnyCustomer() throws Exception {
        Product phone = tracked("phone", 5L);
        Product cable = untracked("cable", 3L);

        mockMvc.perform(subtract().with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                .content(items(phone.getId(), 2, cable.getId(), 10)))
            .andExpect(status().isNoContent());

        assertThat(stockOf(phone)).isEqualTo(3);
        assertThat(stockOf(cable)).isEqualTo(3);
    }

    @Test
    void subtract_duplicateItemsAreMerged() throws Exception {
        Product phone = tracked("phone", 5L);
        mockMvc.perform(subtract().with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                .content(items(phone.getId(), 2, phone.getId(), 3)))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isZero();
    }

    @Test
    void subtract_insufficient_isBadRequest_andNothingChanges() throws Exception {
        Product phone = tracked("phone", 5L);
        Product cable = tracked("cable", 1L);

        mockMvc.perform(subtract().with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                .content(items(phone.getId(), 2, cable.getId(), 2)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("NOT_ENOUGH_STOCK"))
            .andExpect(jsonPath("$.detail").value(containsString(String.valueOf(cable.getId()))))
            .andExpect(jsonPath("$.fieldErrors[0]").value("productId " + cable.getId()));

        assertThat(stockOf(phone)).isEqualTo(5);
        assertThat(stockOf(cable)).isEqualTo(1);
    }

    @Test
    void subtract_trackedWithoutQuantity_isNotEnoughStock() throws Exception {
        Product phone = tracked("phone", null);
        mockMvc.perform(subtract().with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                .content(items(phone.getId(), 1)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("NOT_ENOUGH_STOCK"));
    }

    @Test
    void subtract_invalidInput() throws Exception {
        Product phone = tracked("phone", 5L);
        for (String body : List.of("[]", items(phone.getId(), 0), items(phone.getId(), -1), items("null", 1),
            "[{\"productId\":" + phone.getId() + "}]")) {
            mockMvc.perform(subtract().with(customer("c-1")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(subtract().with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                .content(items(phone.getId(), 1, 999999, 1)))
            .andExpect(status().isNotFound());
        mockMvc.perform(put(SUBTRACT).param("reference", "bad reference!").with(customer("c-1"))
                .contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 1)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(subtract().contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 1)))
            .andExpect(status().is4xxClientError());
        assertThat(stockOf(phone)).isEqualTo(5);
    }

    @Test
    void concurrentSubtracts_forTheLastUnits_neverOversell() throws Exception {
        Product phone = tracked("phone", 5L);
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String userId = "c-" + i;
                Callable<Integer> call = () -> {
                    start.await();
                    return mockMvc.perform(subtract().with(customer(userId))
                            .contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 1)))
                        .andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get());
            }
            assertThat(statuses).filteredOn(code -> code == 204).hasSize(5);
            assertThat(statuses).filteredOn(code -> code == 400).hasSize(5);
        } finally {
            pool.shutdownNow();
        }
        assertThat(stockOf(phone)).isZero();
    }

    // ---------- restore-quantity ----------

    @Test
    void restore_givesBackExactlyWhatTheSameCallerSubtractedUnderTheReference_once() throws Exception {
        Product phone = tracked("phone", 5L);
        Product cable = untracked("cable", 3L);
        mockMvc.perform(put(SUBTRACT).param("reference", "order-abc_1").with(customer("c-1"))
                .contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 2, cable.getId(), 1)))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(3);

        // someone else cannot restore it
        mockMvc.perform(put(RESTORE).param("reference", "order-abc_1").with(customer("c-2")))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(3);

        mockMvc.perform(put(RESTORE).param("reference", "order-abc_1").with(customer("c-1")))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(5);
        assertThat(stockOf(cable)).isEqualTo(3);

        // idempotent: a second restore adds nothing
        mockMvc.perform(put(RESTORE).param("reference", "order-abc_1").with(customer("c-1")))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(5);
    }

    @Test
    void restore_unknownReference_isNoOp_andReferenceIsRequired() throws Exception {
        Product phone = tracked("phone", 5L);
        mockMvc.perform(put(RESTORE).param("reference", "never-used").with(customer("c-1")))
            .andExpect(status().isNoContent());
        mockMvc.perform(put(RESTORE).with(customer("c-1"))).andExpect(status().isBadRequest());
        mockMvc.perform(put(RESTORE).param("reference", "x".repeat(65)).with(customer("c-1")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(RESTORE).param("reference", "never-used")).andExpect(status().is4xxClientError());
        assertThat(stockOf(phone)).isEqualTo(5);
    }

    @Test
    void subtract_reusedReference_isBadRequest() throws Exception {
        Product phone = tracked("phone", 5L);
        mockMvc.perform(put(SUBTRACT).param("reference", "order-1").with(customer("c-1"))
                .contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 1)))
            .andExpect(status().isNoContent());
        mockMvc.perform(put(SUBTRACT).param("reference", "order-1").with(customer("c-1"))
                .contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 1)))
            .andExpect(status().isBadRequest());
        assertThat(stockOf(phone)).isEqualTo(4);
    }

    @Test
    void subtract_withoutReference_isBadRequest() throws Exception {
        Product phone = tracked("phone", 5L);
        mockMvc.perform(put(SUBTRACT).with(customer("c-1"))
                .contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 1)))
            .andExpect(status().isBadRequest());
        assertThat(stockOf(phone)).isEqualTo(5);
    }

    @Test
    void concurrentSubtracts_withTheSameReference_takeOnce_andTheOthersAreBadRequest() throws Exception {
        Product phone = tracked("phone", 20L);
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<String> call = () -> {
                    start.await();
                    var response = mockMvc.perform(put(SUBTRACT).param("reference", "order-same")
                            .with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                            .content(items(phone.getId(), 1)))
                        .andReturn().getResponse();
                    return response.getStatus() + " " + response.getContentAsString();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<String> answers = new ArrayList<>();
            for (Future<String> result : results) {
                answers.add(result.get());
            }
            assertThat(answers).filteredOn(answer -> answer.startsWith("204")).hasSize(1);
            assertThat(answers).filteredOn(answer -> answer.startsWith("400")
                && answer.contains("has already been used")).hasSize(threads - 1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(stockOf(phone)).isEqualTo(19);
    }

    @Test
    void failedSubtract_recordsNoReservation() throws Exception {
        Product phone = tracked("phone", 1L);
        mockMvc.perform(put(SUBTRACT).param("reference", "order-2").with(customer("c-1"))
                .contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 2)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(RESTORE).param("reference", "order-2").with(customer("c-1")))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(1);
    }

    // ---------- storefront view models ----------

    @Test
    void storefrontViewModels_exposeStockAvailability() throws Exception {
        Product inStock = tracked("in-stock", 4L);
        Product soldOut = tracked("sold-out", 0L);
        Product untracked = untracked("untracked", 0L);

        mockMvc.perform(get("/storefront/product/in-stock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockTrackingEnabled").value(true))
            .andExpect(jsonPath("$.stockAvailable").value(true))
            .andExpect(jsonPath("$.stockQuantity").value(4));
        mockMvc.perform(get("/storefront/product/sold-out"))
            .andExpect(jsonPath("$.stockAvailable").value(false))
            .andExpect(jsonPath("$.stockQuantity").value(0));
        mockMvc.perform(get("/storefront/product/untracked"))
            .andExpect(jsonPath("$.stockTrackingEnabled").value(false))
            .andExpect(jsonPath("$.stockAvailable").value(true))
            .andExpect(jsonPath("$.stockQuantity").value(nullValue()));

        mockMvc.perform(get("/storefront/products/list-featured")
                .param("productId", inStock.getId() + "," + soldOut.getId() + "," + untracked.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.slug == 'in-stock')].stockAvailable").value(true))
            .andExpect(jsonPath("$[?(@.slug == 'in-stock')].stockQuantity").value(4))
            .andExpect(jsonPath("$[?(@.slug == 'sold-out')].stockAvailable").value(false))
            .andExpect(jsonPath("$[?(@.slug == 'untracked')].stockAvailable").value(true))
            .andExpect(jsonPath("$[?(@.slug == 'untracked')].stockTrackingEnabled").value(false));

        mockMvc.perform(get("/storefront/products").param("pageSize", "10"))
            .andExpect(jsonPath("$.productContent[?(@.slug == 'sold-out')].stockAvailable").value(false));
    }

    @Test
    void adminDetail_exposesStockQuantity() throws Exception {
        Product phone = tracked("phone", 7L);
        mockMvc.perform(get("/backoffice/products/" + phone.getId()).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockTrackingEnabled").value(true))
            .andExpect(jsonPath("$.stockQuantity").value(7));
    }

    // ---------- for-warehouse (inventory's product lookup) ----------

    @Test
    void forWarehouse_filtersByNameSkuAndSelection_adminOnly() throws Exception {
        Product phone = tracked("phone", 1L);
        phone.setSku("PH-1");
        fixtures.save(phone);
        Product cable = tracked("cable", 1L);
        cable.setSku("CA-1");
        fixtures.save(cable);
        Product case1 = tracked("phone-case", 1L);
        case1.setSku("PC-1");
        fixtures.save(case1);

        mockMvc.perform(get("/backoffice/products/for-warehouse").with(admin())
                .param("name", "PHONE").param("sku", "").param("selection", "ALL"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("phone", "phone-case")));
        mockMvc.perform(get("/backoffice/products/for-warehouse").with(admin())
                .param("name", "").param("sku", "ph-").param("selection", "ALL"))
            .andExpect(jsonPath("$[*].sku").value(containsInAnyOrder("PH-1")));
        mockMvc.perform(get("/backoffice/products/for-warehouse").with(admin())
                .param("productIds", phone.getId() + "," + cable.getId()).param("selection", "YES"))
            .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("phone", "cable")))
            .andExpect(jsonPath("$[0].stockTrackingEnabled").value(true))
            .andExpect(jsonPath("$[0].stockQuantity").value(1));
        mockMvc.perform(get("/backoffice/products/for-warehouse").with(admin())
                .param("productIds", phone.getId() + "," + cable.getId()).param("selection", "NO"))
            .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("phone-case")));
        mockMvc.perform(get("/backoffice/products/for-warehouse").with(admin()).param("selection", "YES"))
            .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/backoffice/products/for-warehouse").with(customer("c-1")))
            .andExpect(status().isForbidden());
        // a parent with options holds no stock (its variations do)
        Product parent = tracked("phone-pro", 1L);
        parent.setHasOptions(true);
        fixtures.save(parent);
        fixtures.variation(parent, "phone-pro-blue", "phone-pro-blue", 12);
        mockMvc.perform(get("/backoffice/products/for-warehouse").with(admin()).param("name", "phone-pro"))
            .andExpect(jsonPath("$[*].name").value(containsInAnyOrder("phone-pro-blue")));
    }
}
