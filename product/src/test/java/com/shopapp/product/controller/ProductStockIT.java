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
import com.shopapp.product.support.AbstractStorefrontIT;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Stock quantity endpoints: update-quantity (inventory, ADMIN), subtract-quantity and restore-quantity (order, any
 * signed-in user), and the stock fields of the storefront view models.
 */
class ProductStockIT extends AbstractStorefrontIT {

    private static final String UPDATE = "/backoffice/products/update-quantity";
    private static final String SUBTRACT = "/backoffice/products/subtract-quantity";
    private static final String RESTORE = "/backoffice/products/restore-quantity";

    @Autowired
    private ProductRepository productRepository;

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor customer(String userId) {
        return jwt().jwt(token -> token.subject(userId)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private Product tracked(String slug, Long quantity) {
        Product product = fixtures.product(slug, slug, 10);
        product.setStockTrackingEnabled(true);
        product.setStockQuantity(quantity);
        return fixtures.save(product);
    }

    private Product untracked(String slug, Long quantity) {
        Product product = fixtures.product(slug, slug, 10);
        product.setStockTrackingEnabled(false);
        product.setStockQuantity(quantity);
        return fixtures.save(product);
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

    // ---------- subtract-quantity ----------

    @Test
    void subtract_decreasesTrackedProducts_leavesUntrackedAlone_forAnyCustomer() throws Exception {
        Product phone = tracked("phone", 5L);
        Product cable = untracked("cable", 3L);

        mockMvc.perform(put(SUBTRACT).with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                .content(items(phone.getId(), 2, cable.getId(), 10)))
            .andExpect(status().isNoContent());

        assertThat(stockOf(phone)).isEqualTo(3);
        assertThat(stockOf(cable)).isEqualTo(3);
    }

    @Test
    void subtract_duplicateItemsAreMerged() throws Exception {
        Product phone = tracked("phone", 5L);
        mockMvc.perform(put(SUBTRACT).with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                .content(items(phone.getId(), 2, phone.getId(), 3)))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isZero();
    }

    @Test
    void subtract_insufficient_isBadRequest_andNothingChanges() throws Exception {
        Product phone = tracked("phone", 5L);
        Product cable = tracked("cable", 1L);

        mockMvc.perform(put(SUBTRACT).with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
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
        mockMvc.perform(put(SUBTRACT).with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                .content(items(phone.getId(), 1)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("NOT_ENOUGH_STOCK"));
    }

    @Test
    void subtract_invalidInput() throws Exception {
        Product phone = tracked("phone", 5L);
        for (String body : List.of("[]", items(phone.getId(), 0), items(phone.getId(), -1), items("null", 1),
            "[{\"productId\":" + phone.getId() + "}]")) {
            mockMvc.perform(put(SUBTRACT).with(customer("c-1")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put(SUBTRACT).with(customer("c-1")).contentType(MediaType.APPLICATION_JSON)
                .content(items(phone.getId(), 1, 999999, 1)))
            .andExpect(status().isNotFound());
        mockMvc.perform(put(SUBTRACT).param("reference", "bad reference!").with(customer("c-1"))
                .contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 1)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(SUBTRACT).contentType(MediaType.APPLICATION_JSON).content(items(phone.getId(), 1)))
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
                    return mockMvc.perform(put(SUBTRACT).with(customer(userId))
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
            .andExpect(jsonPath("$[0].stockTrackingEnabled").value(true));
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
