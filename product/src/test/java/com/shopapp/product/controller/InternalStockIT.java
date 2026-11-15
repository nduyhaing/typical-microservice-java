package com.shopapp.product.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.product.model.Product;
import com.shopapp.product.repository.ProductRepository;
import com.shopapp.product.support.AbstractStorefrontIT;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Internal restore-quantity (decisions.md 5-5): the order service gives a cancelled order's stock back with its own
 * client-credentials token (realm role SERVICE), whoever placed the order; once per reference.
 */
class InternalStockIT extends AbstractStorefrontIT {

    private static final String SUBTRACT = "/backoffice/products/subtract-quantity";
    private static final String INTERNAL_RESTORE = "/internal/products/restore-quantity";

    @Autowired
    private ProductRepository productRepository;

    private static RequestPostProcessor customer(String userId) {
        return jwt().jwt(token -> token.subject(userId)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-1")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static RequestPostProcessor service() {
        return jwt().jwt(token -> token.subject("service-account-order-service"))
            .authorities(new SimpleGrantedAuthority("ROLE_SERVICE_GOODS_BACK"));
    }

    private Product tracked(String slug, Long quantity) {
        Product product = fixtures.product(slug, slug, 10);
        product.setStockTrackingEnabled(true);
        return fixtures.withStock(fixtures.save(product), quantity);
    }

    private long stockOf(Product product) {
        Long quantity = productRepository.findById(product.getId()).orElseThrow().getStockQuantity();
        return quantity == null ? 0 : quantity;
    }

    private void subtract(String customer, String reference, Product product, int quantity) throws Exception {
        mockMvc.perform(put(SUBTRACT).param("reference", reference).with(customer(customer))
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}]"))
            .andExpect(status().isNoContent());
    }

    @Test
    void serviceRestoresAnyCustomersReservation_once() throws Exception {
        Product phone = tracked("phone", 5L);
        subtract("c-1", "order-ref-1", phone, 2);
        assertThat(stockOf(phone)).isEqualTo(3);

        mockMvc.perform(put(INTERNAL_RESTORE).param("reference", "order-ref-1").with(service()))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(5);

        // again (a retried cancel): nothing more
        mockMvc.perform(put(INTERNAL_RESTORE).param("reference", "order-ref-1").with(service()))
            .andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(5);
        // and the customer-bound restore cannot give it back a second time either
        mockMvc.perform(put("/backoffice/products/restore-quantity").param("reference", "order-ref-1")
            .with(customer("c-1"))).andExpect(status().isNoContent());
        assertThat(stockOf(phone)).isEqualTo(5);
    }

    @Test
    void concurrentRestores_giveBackOnce() throws Exception {
        Product phone = tracked("phone", 5L);
        subtract("c-1", "order-ref-2", phone, 3);

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                calls.add(() -> mockMvc.perform(put(INTERNAL_RESTORE).param("reference", "order-ref-2")
                    .with(service())).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> result : pool.invokeAll(calls)) {
                assertThat(result.get()).isEqualTo(204);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(stockOf(phone)).isEqualTo(5);
    }

    @Test
    void unknownReference_isNoOp_andTheReferenceIsValidated() throws Exception {
        mockMvc.perform(put(INTERNAL_RESTORE).param("reference", "never-used").with(service()))
            .andExpect(status().isNoContent());
        mockMvc.perform(put(INTERNAL_RESTORE).with(service())).andExpect(status().isBadRequest());
        mockMvc.perform(put(INTERNAL_RESTORE).param("reference", "x".repeat(65)).with(service()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void customersAndAdmins_areRefused() throws Exception {
        Product phone = tracked("phone", 5L);
        subtract("c-1", "order-ref-3", phone, 2);

        mockMvc.perform(put(INTERNAL_RESTORE).param("reference", "order-ref-3").with(customer("c-1")))
            .andExpect(status().isForbidden());
        mockMvc.perform(put(INTERNAL_RESTORE).param("reference", "order-ref-3").with(admin()))
            .andExpect(status().isForbidden());
        mockMvc.perform(put(INTERNAL_RESTORE).param("reference", "order-ref-3").with(csrf()))
            .andExpect(status().isUnauthorized());
        // another service's capability (the payment service's) and the old catch-all SERVICE role (5-66)
        for (String role : java.util.List.of("ROLE_SERVICE_ORDER_PAYMENT", "ROLE_SERVICE")) {
            mockMvc.perform(put(INTERNAL_RESTORE).param("reference", "order-ref-3")
                    .with(jwt().authorities(new SimpleGrantedAuthority(role))))
                .andExpect(status().isForbidden());
        }
        assertThat(stockOf(phone)).isEqualTo(3);
    }
}
