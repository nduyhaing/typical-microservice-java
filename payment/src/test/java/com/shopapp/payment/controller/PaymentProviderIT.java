package com.shopapp.payment.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/** Payment providers: seeded COD and PAYPAL, storefront list enabled and available only, admin-only writes. */
class PaymentProviderIT extends PaymentItSupport {

    private static final String STRIPE = "{\"id\":\"STRIPE\",\"enabled\":true,\"name\":\"Stripe\","
        + "\"configureUrl\":\"stripe-config\",\"landingViewComponentName\":\"stripe\",\"additionalSettings\":null}";

    @Test
    void storefront_listsEnabledProviders_publicly() throws Exception {
        mockMvc.perform(get("/storefront/payment-providers"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("COD"))
            .andExpect(jsonPath("$[0].name").value("Cash on Delivery"))
            .andExpect(jsonPath("$[1].id").value("PAYPAL"))
            .andExpect(jsonPath("$[0].additionalSettings").doesNotExist());
    }

    @Test
    void storefront_hidesDisabled_andProvidersWithoutAHandler() throws Exception {
        jdbc.update("update payment_provider set enabled = false where id = 'PAYPAL'");
        mockMvc.perform(json(post("/backoffice/payment-providers"), STRIPE).with(admin()))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/storefront/payment-providers"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("COD"));
    }

    @Test
    void admin_listsAllProviders_withEnabledAndAvailable() throws Exception {
        PAYPAL_CONFIGURED.set(false);

        mockMvc.perform(get("/backoffice/payment-providers").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("COD"))
            .andExpect(jsonPath("$[0].available").value(true))
            .andExpect(jsonPath("$[1].id").value("PAYPAL"))
            .andExpect(jsonPath("$[1].enabled").value(true))
            .andExpect(jsonPath("$[1].available").value(false));
    }

    @Test
    void admin_createsAndUpdatesProviders() throws Exception {
        mockMvc.perform(json(post("/backoffice/payment-providers"), STRIPE).with(admin()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value("STRIPE"))
            .andExpect(jsonPath("$.enabled").value(true));
        mockMvc.perform(json(post("/backoffice/payment-providers"), STRIPE).with(admin()))
            .andExpect(status().isConflict());

        mockMvc.perform(json(put("/backoffice/payment-providers"),
                "{\"id\":\"COD\",\"enabled\":false,\"name\":\"Pay on delivery\",\"configureUrl\":\"cod-config\"}")
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Pay on delivery"))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(get("/storefront/payment-providers"))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value("PAYPAL"));

        mockMvc.perform(json(put("/backoffice/payment-providers"), "{\"id\":\"NOPE\",\"enabled\":true,\"name\":\"x\"}")
                .with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void invalidProviders_areRefused() throws Exception {
        mockMvc.perform(json(post("/backoffice/payment-providers"), "{\"id\":\"lower\",\"name\":\"x\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/backoffice/payment-providers"), "{\"id\":\"OK_ID\",\"name\":\"\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/backoffice/payment-providers"),
                "{\"id\":\"OK_ID\",\"name\":\"x\",\"additionalSettings\":\"" + "a".repeat(2001) + "\"}")
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void writes_andTheAdminList_areAdminOnly() throws Exception {
        mockMvc.perform(json(post("/backoffice/payment-providers"), STRIPE).with(customer("u-1")))
            .andExpect(status().isForbidden());
        mockMvc.perform(json(put("/backoffice/payment-providers"), STRIPE).with(customer("u-1")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/payment-providers").with(customer("u-1")))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/payment-providers"))
            .andExpect(status().isUnauthorized());
    }
}
