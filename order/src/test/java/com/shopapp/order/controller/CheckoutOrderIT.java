package com.shopapp.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.order.model.enumeration.CheckoutState;
import com.shopapp.order.repository.CheckoutRepository;
import com.shopapp.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CheckoutOrderIT extends OrderItSupport {

    @Autowired
    private CheckoutRepository checkoutRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void createCheckout_pricesFromProductService() throws Exception {
        mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":[{\"productId\":25,\"quantity\":2},{\"productId\":26,\"quantity\":1}]}")
                .with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(1657.0))
            .andExpect(jsonPath("$.checkoutState").value("PENDING"))
            .andExpect(jsonPath("$.checkoutItemVms[*].productName")
                .value(containsInAnyOrder("Galaxy S24 Violet", "USB-C Cable")));
    }

    @Test
    void createCheckout_notOrderable_returns404() throws Exception {
        mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":[{\"productId\":1,\"quantity\":1}]}").with(customer("u-1")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Product 1 is not available for ordering"));

        assertThat(checkoutRepository.findAll()).isEmpty();
    }

    @Test
    void createCheckout_productDown_returns5xx() throws Exception {
        PRODUCT_DOWN.set(true);

        mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":[{\"productId\":25,\"quantity\":1}]}").with(customer("u-1")))
            .andExpect(status().is5xxServerError());

        assertThat(checkoutRepository.findAll()).isEmpty();
    }

    @Test
    void createCheckout_quantityOutOfRange_returns400() throws Exception {
        mockMvc.perform(json(post("/storefront/checkouts"),
                "{\"checkoutItemPostVms\":[{\"productId\":25,\"quantity\":1001}]}").with(customer("u-1")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/storefront/checkouts"), "{\"checkoutItemPostVms\":[]}").with(customer("u-1")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getCheckout_foreign_returns403() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(get("/storefront/checkouts/{id}", checkoutId).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(819.0));
        mockMvc.perform(get("/storefront/checkouts/{id}", checkoutId).with(customer("u-2")))
            .andExpect(status().isForbidden());
    }

    @Test
    void placeOrder_createsPendingPaymentOrderFromCheckout() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":2}]");

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.orderStatus").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
            .andExpect(jsonPath("$.totalPrice").value(1638.0))
            .andExpect(jsonPath("$.email").value("u-1@shopapp.local"))
            .andExpect(jsonPath("$.orderItemVms[0].productName").value("Galaxy S24 Violet"))
            .andExpect(jsonPath("$.shippingAddressVm.city").value("Springfield"))
            .andExpect(jsonPath("$.shippingAddressVm.countryName").value("United States"));

        assertThat(checkoutRepository.findById(checkoutId)).get()
            .extracting(c -> c.getCheckoutState()).isEqualTo(CheckoutState.COMPLETED);
    }

    @Test
    void placeOrder_ignoresClientTotal() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrder(checkoutId, ",\"totalPrice\":1,\"tax\":0,\"discount\":818").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalPrice").value(819.0));
    }

    @Test
    void placeOrder_removesItemsFromCartWithCallersToken() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":2}]");

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isOk());

        assertThat(CART_AUTHORIZATION.get()).isEqualTo("Bearer token-u-1");
        assertThat(CART_BODY.get()).contains("\"productId\":25").contains("\"quantity\":2");
    }

    @Test
    void placeOrder_cartDown_stillCreatesOrder() throws Exception {
        CART_DOWN.set(true);
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isOk());

        assertThat(orderRepository.findByCheckoutId(checkoutId)).isPresent();
    }

    @Test
    void placeOrder_foreignCheckout_returns403() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-2"))).andExpect(status().isForbidden());

        assertThat(orderRepository.findAll()).isEmpty();
    }

    @Test
    void placeOrder_twice_returns400() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isOk());

        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isBadRequest());

        assertThat(orderRepository.findAll()).hasSize(1);
    }

    @Test
    void placeOrder_missingAddress_returns400() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId
                + "\",\"shippingAddressPostVm\":{\"contactName\":\"\",\"phone\":\"1\"},\"paymentMethod\":\"COD\"}")
                .with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }

    @Test
    void myOrders_returnsOnlyOwnNewestFirst() throws Exception {
        String first = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        mockMvc.perform(placeOrder(first, "").with(customer("u-1"))).andExpect(status().isOk());
        String second = createCheckout("u-1", "[{\"productId\":26,\"quantity\":3}]");
        mockMvc.perform(placeOrder(second, "").with(customer("u-1"))).andExpect(status().isOk());
        String other = createCheckout("u-2", "[{\"productId\":26,\"quantity\":1}]");
        mockMvc.perform(placeOrder(other, "").with(customer("u-2"))).andExpect(status().isOk());

        mockMvc.perform(get("/storefront/orders/my-orders").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].checkoutId").value(second))
            .andExpect(jsonPath("$[0].totalPrice").value(57.0))
            .andExpect(jsonPath("$[1].checkoutId").value(first));
    }

    @Test
    void orderByCheckout_returnsOwnOrder() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1"))).andExpect(status().isOk());

        mockMvc.perform(get("/storefront/orders/checkout/{id}", checkoutId).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkoutId").value(checkoutId));
        mockMvc.perform(get("/storefront/orders/checkout/{id}", checkoutId).with(customer("u-2")))
            .andExpect(status().isForbidden());
    }

    @Test
    void anonymous_returns4xx() throws Exception {
        mockMvc.perform(get("/storefront/orders/my-orders")).andExpect(status().is4xxClientError());
        mockMvc.perform(json(post("/storefront/checkouts"), "{\"checkoutItemPostVms\":[{\"productId\":25,\"quantity\":1}]}"))
            .andExpect(status().is4xxClientError());
    }

    @Test
    void placeOrder_concurrentCalls_createExactlyOneOrder() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        int callers = 6;
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(callers);
        java.util.List<java.util.concurrent.Future<Integer>> statuses = new java.util.ArrayList<>();
        for (int i = 0; i < callers; i++) {
            statuses.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1")))
                    .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        java.util.List<Integer> codes = new java.util.ArrayList<>();
        for (java.util.concurrent.Future<Integer> status : statuses) {
            codes.add(status.get());
        }
        pool.shutdown();

        assertThat(codes).filteredOn(code -> code == 200).hasSize(1);
        assertThat(codes).filteredOn(code -> code == 400).hasSize(callers - 1);
        assertThat(orderRepository.findAll()).hasSize(1);
        mockMvc.perform(get("/storefront/orders/checkout/{id}", checkoutId).with(customer("u-1")))
            .andExpect(status().isOk());
    }

    @Test
    void longNotes_return400FieldErrors() throws Exception {
        String longNote = "x".repeat(256);
        mockMvc.perform(json(post("/storefront/checkouts"), "{\"note\":\"" + longNote
                + "\",\"checkoutItemPostVms\":[{\"productId\":25,\"quantity\":1}]}").with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        mockMvc.perform(placeOrder(checkoutId, ",\"note\":\"" + longNote + "\"").with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors").isNotEmpty());
    }
}

