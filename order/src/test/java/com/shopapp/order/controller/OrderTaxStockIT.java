package com.shopapp.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Slice 3c: saved/typed address with location ids, tax per item from tax, stock subtract and compensation. */
class OrderTaxStockIT extends OrderItSupport {

    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final String MIXED_ITEMS = "[{\"productId\":25,\"quantity\":2},{\"productId\":26,\"quantity\":1},"
        + "{\"productId\":27,\"quantity\":1}]";

    private BigDecimal decimal(String sql, Object... args) {
        return jdbc.queryForObject(sql, BigDecimal.class, args);
    }

    private String checkoutState(String checkoutId) {
        return jdbc.queryForObject("select status from checkout where id = ?", String.class, checkoutId);
    }

    private MockHttpServletRequestBuilder placeOrderTyped(String checkoutId, String address) {
        return json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressPostVm\":"
            + address + ",\"paymentMethod\":\"COD\"}");
    }

    private static String typedAddress(String locationIds) {
        return "{\"contactName\":\"Customer One\",\"phone\":\"0901\",\"addressLine1\":\"1 Le Loi\",\"zipCode\":\"700000\","
            + locationIds + "}";
    }

    @Test
    void placeOrder_savedAddress_chargesTaxPerItemFromOneBatchCall() throws Exception {
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        String response = mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subtotal").value(1667.0))
            .andExpect(jsonPath("$.tax").value(163.8))
            .andExpect(jsonPath("$.discount").value(0))
            .andExpect(jsonPath("$.totalPrice").value(1830.8))
            .andExpect(jsonPath("$.shippingAddressVm.contactName").value("Customer One"))
            .andExpect(jsonPath("$.shippingAddressVm.countryName").value("Viet Nam"))
            .andExpect(jsonPath("$.shippingAddressVm.stateOrProvinceName").value("Ho Chi Minh"))
            .andExpect(jsonPath("$.shippingAddressVm.districtName").value("District 1"))
            .andReturn().getResponse().getContentAsString();

        // exact money in the database (numeric(19,2), no float rounding)
        assertThat(decimal("select total_tax from \"order\"")).isEqualByComparingTo("163.80");
        assertThat(decimal("select total_amount from \"order\"")).isEqualByComparingTo("1830.80");
        assertThat(decimal("select total_discount_amount from \"order\"")).isEqualByComparingTo("0");
        assertThat(decimal("select tax_amount from order_item where product_id = 25")).isEqualByComparingTo("163.80");
        assertThat(decimal("select tax_percent from order_item where product_id = 25")).isEqualByComparingTo("10");
        assertThat(decimal("select tax_amount from order_item where product_id = 26")).isEqualByComparingTo("0");
        assertThat(decimal("select tax_amount from order_item where product_id = 27")).isEqualByComparingTo("0");
        assertThat(jdbc.queryForObject("select distinct country_id from order_address", Long.class)).isEqualTo(1L);
        assertThat(JsonPath.<List<Double>>read(response, "$.orderItemVms[?(@.productId == 25)].taxAmount"))
            .containsExactly(163.8);

        // one batch call with the distinct classes (null class left out), the address's location, caller's token
        assertThat(TAX_CALLS).hasSize(1);
        Call tax = TAX_CALLS.getFirst();
        assertThat(tax.query().get("taxClassIds")).containsExactlyInAnyOrder("1", "2");
        assertThat(tax.param("countryId")).isEqualTo("1");
        assertThat(tax.param("stateOrProvinceId")).isEqualTo("10");
        assertThat(tax.param("zipCode")).isEqualTo("700000");
        assertThat(tax.authorization()).isEqualTo("Bearer token-u-1");
        assertThat(ADDRESS_CALLS).extracting(Call::authorization).containsOnly("Bearer token-u-1");
    }

    @Test
    void placeOrder_taxRoundedHalfUpPerItem() throws Exception {
        // 0.25 x 10 % = 0.025 -> 0.03 (HALF_UP; HALF_EVEN would give 0.02)
        String checkoutId = createCheckout("u-1", "[{\"productId\":28,\"quantity\":1}]");

        mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tax").value(0.03))
            .andExpect(jsonPath("$.totalPrice").value(0.28));

        assertThat(decimal("select total_amount from \"order\"")).isEqualByComparingTo("0.28");
    }

    @Test
    void placeOrder_onlyUntaxedProducts_skipsTaxCall() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":27,\"quantity\":3}]");

        mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tax").value(0))
            .andExpect(jsonPath("$.totalPrice").value(30.0));

        assertThat(TAX_CALLS).isEmpty();
    }

    @Test
    void placeOrder_otherUsersAddress_returns404AndNoOrder() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 200).with(customer("u-1")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.detail").value("Address 200 is not found"));

        assertThat(orderCount()).isZero();
        assertThat(SUBTRACT_CALLS).isEmpty();
        assertThat(checkoutState(checkoutId)).isEqualTo("PENDING");
    }

    @Test
    void placeOrder_typedAddress_resolvesLocationNamesAndTax() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrderTyped(checkoutId,
                typedAddress("\"countryId\":1,\"stateOrProvinceId\":10,\"districtId\":100,\"countryName\":\"Fake\""))
                .with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tax").value(81.9))
            .andExpect(jsonPath("$.totalPrice").value(900.9))
            .andExpect(jsonPath("$.shippingAddressVm.countryName").value("Viet Nam"))
            .andExpect(jsonPath("$.shippingAddressVm.stateOrProvinceName").value("Ho Chi Minh"))
            .andExpect(jsonPath("$.shippingAddressVm.districtName").value("District 1"));
    }

    @Test
    void placeOrder_typedAddressWithUnknownLocation_returns400() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrderTyped(checkoutId, typedAddress("\"countryId\":999")).with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Country 999 is not found"));
        mockMvc.perform(placeOrderTyped(checkoutId, typedAddress("\"countryId\":2,\"stateOrProvinceId\":10"))
                .with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("State or province 10 is not in country 2"));
        mockMvc.perform(placeOrderTyped(checkoutId, typedAddress("\"countryId\":1,\"districtId\":100"))
                .with(customer("u-1")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(placeOrderTyped(checkoutId,
                typedAddress("\"countryId\":1,\"stateOrProvinceId\":10,\"districtId\":5")).with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("District 5 is not in state or province 10"));
        mockMvc.perform(placeOrderTyped(checkoutId, typedAddress("\"stateOrProvinceId\":10")).with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        assertThat(orderCount()).isZero();
        assertThat(SUBTRACT_CALLS).isEmpty();
    }

    @Test
    void placeOrder_needsExactlyOneAddressChoice() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId
                + "\",\"paymentMethod\":\"COD\"}").with(customer("u-1")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId
                + "\",\"shippingAddressId\":100,\"shippingAddressPostVm\":" + ADDRESS + ",\"paymentMethod\":\"COD\"}")
                .with(customer("u-1")))
            .andExpect(status().isBadRequest());

        assertThat(orderCount()).isZero();
    }

    @Test
    void placeOrder_taxDown_returns5xxAndSavesNothing() throws Exception {
        TAX_DOWN.set(true);
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.detail").value("Tax service is unavailable, please try again later"));

        assertThat(orderCount()).isZero();
        assertThat(SUBTRACT_CALLS).isEmpty();
        assertThat(checkoutState(checkoutId)).isEqualTo("PENDING");
    }

    @Test
    void placeOrder_locationDown_returns5xxAndSavesNothing() throws Exception {
        LOCATION_DOWN.set(true);
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().is5xxServerError());
        mockMvc.perform(placeOrder(checkoutId, "").with(customer("u-1")))
            .andExpect(status().is5xxServerError());

        assertThat(orderCount()).isZero();
        assertThat(SUBTRACT_CALLS).isEmpty();
    }

    @Test
    void placeOrder_subtractsStockWithServerSideReference() throws Exception {
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        String response = mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertThat(SUBTRACT_CALLS).hasSize(1);
        Call subtract = SUBTRACT_CALLS.getFirst();
        String reference = subtract.param("reference");
        assertThat(reference).matches(UUID_PATTERN);
        assertThat(subtract.authorization()).isEqualTo("Bearer token-u-1");
        assertThat(JsonPath.<List<Integer>>read(subtract.body(), "$[?(@.productId == 25)].quantity")).containsExactly(2);
        assertThat(JsonPath.<List<Integer>>read(subtract.body(), "$[?(@.productId == 26)].quantity")).containsExactly(1);
        assertThat(JsonPath.<List<Integer>>read(subtract.body(), "$[?(@.productId == 27)].quantity")).containsExactly(1);
        assertThat(RESTORE_CALLS).isEmpty();
        // the reference never reaches the customer (else they could give the stock back, 3c-22)
        assertThat(response).doesNotContain(reference);
    }

    @Test
    void placeOrder_clientReferenceIgnored() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(json(post("/storefront/orders"), "{\"checkoutId\":\"" + checkoutId
                + "\",\"shippingAddressId\":100,\"paymentMethod\":\"COD\",\"reference\":\"mine\"}")
                .with(customer("u-1")))
            .andExpect(status().isOk());

        assertThat(SUBTRACT_CALLS.getFirst().param("reference")).matches(UUID_PATTERN);
    }

    @Test
    void placeOrder_notEnoughStock_returns400AndSavesNothing() throws Exception {
        SUBTRACT_STATUS.set(400);
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":2}]");

        mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Not enough stock for Galaxy S24 Violet (product 25)"));

        assertThat(orderCount()).isZero();
        assertThat(checkoutState(checkoutId)).isEqualTo("PENDING");
        assertThat(RESTORE_CALLS).isEmpty();
    }

    @Test
    void placeOrder_failureAfterSubtract_restoresStockWithSameReference() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        // an order row for this checkout already exists (e.g. written by another instance): the unique index on
        // order.checkout_id fails the insert after the stock was taken
        Long addressId = jdbc.queryForObject("insert into order_address (contact_name, phone, address_line1) "
            + "values ('x', 'y', 'z') returning id", Long.class);
        jdbc.update("insert into \"order\" (number_item, checkout_id, shipping_address_id, billing_address_id) "
            + "values (1, ?, ?, ?)", checkoutId, addressId, addressId);

        mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Checkout " + checkoutId + " has already been ordered"));

        assertThat(SUBTRACT_CALLS).hasSize(1);
        assertThat(RESTORE_CALLS).hasSize(1);
        assertThat(RESTORE_CALLS.getFirst().param("reference")).isEqualTo(SUBTRACT_CALLS.getFirst().param("reference"));
        assertThat(RESTORE_CALLS.getFirst().authorization()).isEqualTo("Bearer token-u-1");
        assertThat(orderCount()).isEqualTo(1);
        assertThat(checkoutState(checkoutId)).isEqualTo("PENDING");
    }

    @Test
    void placeOrder_productDownDuringSubtract_returns5xxAndRestores() throws Exception {
        // a 5xx or timeout leaves it unknown whether stock was taken: restore is safe (only gives back what was taken)
        SUBTRACT_STATUS.set(503);
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().is5xxServerError());

        assertThat(orderCount()).isZero();
        assertThat(RESTORE_CALLS).hasSize(1);
        assertThat(RESTORE_CALLS.getFirst().param("reference")).isEqualTo(SUBTRACT_CALLS.getFirst().param("reference"));
    }

    @Test
    void placeOrder_concurrentDoubleSubmit_subtractsStockOnce() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");
        int callers = 6;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        List<Future<Integer>> statuses = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            statuses.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
                    .andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> codes = new ArrayList<>();
        for (Future<Integer> code : statuses) {
            codes.add(code.get());
        }
        pool.shutdown();

        assertThat(codes).filteredOn(code -> code == 200).hasSize(1);
        assertThat(codes).filteredOn(code -> code == 400).hasSize(callers - 1);
        assertThat(orderCount()).isEqualTo(1);
        // the later calls wait on the checkout row lock, then see the order and never take stock
        assertThat(SUBTRACT_CALLS.size() - RESTORE_CALLS.size()).isEqualTo(1);
        assertThat(SUBTRACT_CALLS).hasSize(1);
    }

    @Test
    void preview_returnsServerTotalsMatchingThePlacedOrder() throws Exception {
        String checkoutId = createCheckout("u-1", MIXED_ITEMS);

        String preview = mockMvc.perform(json(post("/storefront/orders/preview"),
                "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressId\":100}").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkoutId").value(checkoutId))
            .andExpect(jsonPath("$.subtotal").value(1667.0))
            .andExpect(jsonPath("$.totalTax").value(163.8))
            .andExpect(jsonPath("$.discount").value(0))
            .andExpect(jsonPath("$.total").value(1830.8))
            .andExpect(jsonPath("$.items.length()").value(3))
            .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Double>>read(preview, "$.items[?(@.productId == 25)].taxAmount")).containsExactly(163.8);
        assertThat(JsonPath.<List<Double>>read(preview, "$.items[?(@.productId == 25)].taxPercent")).containsExactly(10.0);
        assertThat(JsonPath.<List<Double>>read(preview, "$.items[?(@.productId == 25)].subtotal")).containsExactly(1638.0);
        // a preview takes no stock and creates no order
        assertThat(SUBTRACT_CALLS).isEmpty();
        assertThat(orderCount()).isZero();

        String order = mockMvc.perform(placeOrderWithSavedAddress(checkoutId, 100).with(customer("u-1")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Double>read(order, "$.totalPrice")).isEqualTo(JsonPath.<Double>read(preview, "$.total"));
        assertThat(JsonPath.<Double>read(order, "$.tax")).isEqualTo(JsonPath.<Double>read(preview, "$.totalTax"));
    }

    @Test
    void preview_typedAddressInCountryWithoutRates_hasNoTax() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(json(post("/storefront/orders/preview"),
                "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressPostVm\":" + ADDRESS + "}")
                .with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalTax").value(0))
            .andExpect(jsonPath("$.total").value(819.0));
    }

    @Test
    void preview_foreignCheckoutOrAddress_isRefused() throws Exception {
        String checkoutId = createCheckout("u-1", "[{\"productId\":25,\"quantity\":1}]");

        mockMvc.perform(json(post("/storefront/orders/preview"),
                "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressId\":200}").with(customer("u-2")))
            .andExpect(status().isForbidden());
        mockMvc.perform(json(post("/storefront/orders/preview"),
                "{\"checkoutId\":\"" + checkoutId + "\",\"shippingAddressId\":200}").with(customer("u-1")))
            .andExpect(status().isNotFound());
    }
}
