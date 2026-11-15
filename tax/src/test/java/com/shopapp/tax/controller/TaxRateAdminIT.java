package com.shopapp.tax.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class TaxRateAdminIT extends AbstractTaxIT {

    @Test
    void crud_happyPath() throws Exception {
        long standard = createTaxClass("Standard");
        long reduced = createTaxClass("Reduced");

        mockMvc.perform(post("/backoffice/tax-rates").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(rateJson(standard, "8.875", 1L, 2L, " 70000 ")))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString("/backoffice/tax-rates/1")))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.rate").value(8.875))
            .andExpect(jsonPath("$.zipCode").value("70000"))
            .andExpect(jsonPath("$.taxClassId").value(standard))
            .andExpect(jsonPath("$.stateOrProvinceId").value(2))
            .andExpect(jsonPath("$.countryId").value(1));
        createTaxRate(reduced, "5", 1, null, null);

        mockMvc.perform(get("/backoffice/tax-rates/1").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rate").value(8.875));
        mockMvc.perform(get("/backoffice/tax-rates/paging?pageNo=0&pageSize=10").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taxRateGetDetailContent", hasSize(2)))
            .andExpect(jsonPath("$.taxRateGetDetailContent[0].taxClassName").value("Standard"))
            .andExpect(jsonPath("$.taxRateGetDetailContent[0].stateOrProvinceId").value(2))
            .andExpect(jsonPath("$.taxRateGetDetailContent[1].taxClassName").value("Reduced"))
            .andExpect(jsonPath("$.taxRateGetDetailContent[1].stateOrProvinceId").doesNotExist())
            .andExpect(jsonPath("$.taxRateGetDetailContent[1].countryId").value(1))
            .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(put("/backoffice/tax-rates/1").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(rateJson(reduced, "10", 3L, null, "")))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/tax-rates/1").with(admin()))
            .andExpect(jsonPath("$.rate").value(10.0))
            .andExpect(jsonPath("$.taxClassId").value(reduced))
            .andExpect(jsonPath("$.countryId").value(3))
            .andExpect(jsonPath("$.stateOrProvinceId").doesNotExist())
            .andExpect(jsonPath("$.zipCode").doesNotExist());

        mockMvc.perform(delete("/backoffice/tax-rates/1").with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/tax-rates/1").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/backoffice/tax-rates/1").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(put("/backoffice/tax-rates/1").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(rateJson(reduced, "10", 3L, null, null)))
            .andExpect(status().isNotFound());
    }

    @Test
    void unknownTaxClass_isBadRequest() throws Exception {
        mockMvc.perform(post("/backoffice/tax-rates").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(rateJson(99L, "10", 1L, null, null)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("99")));
    }

    @Test
    void sameClassAndLocationTwice_isBadRequest() throws Exception {
        long standard = createTaxClass("Standard");
        createTaxRate(standard, "10", 1, 2L, "70000");
        long other = createTaxRate(standard, "8", 1, 2L, null);
        mockMvc.perform(post("/backoffice/tax-rates").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(rateJson(standard, "9", 1L, 2L, "70000")))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/backoffice/tax-rates/" + other).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(rateJson(standard, "9", 1L, 2L, "70000")))
            .andExpect(status().isBadRequest());
        // updating a rate in place keeps its own location
        mockMvc.perform(put("/backoffice/tax-rates/" + other).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(rateJson(standard, "9", 1L, 2L, null)))
            .andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"taxClassId\":1,\"rate\":-0.01,\"countryId\":1}",
        "{\"taxClassId\":1,\"rate\":100.01,\"countryId\":1}",
        "{\"taxClassId\":1,\"rate\":1.00001,\"countryId\":1}",
        "{\"taxClassId\":1,\"countryId\":1}",
        "{\"taxClassId\":1,\"rate\":10}",
        "{\"rate\":10,\"countryId\":1}",
        "{\"taxClassId\":1,\"rate\":10,\"countryId\":1,\"zipCode\":\"12345678901234567890123456\"}",
        "{\"taxClassId\":1,\"rate\":\"ten\",\"countryId\":1}"
    })
    void invalidBody_isBadRequest(String body) throws Exception {
        createTaxClass("Standard");
        mockMvc.perform(post("/backoffice/tax-rates").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void boundaryRates_areAccepted() throws Exception {
        long standard = createTaxClass("Standard");
        createTaxRate(standard, "0", 1, null, null);
        createTaxRate(standard, "100", 2, null, null);
    }

    @ParameterizedTest
    @ValueSource(strings = {"pageNo=-1", "pageSize=0", "pageSize=101", "pageSize=x"})
    void pagingOutOfBounds_isBadRequest(String query) throws Exception {
        mockMvc.perform(get("/backoffice/tax-rates/paging?" + query).with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void customer_isForbidden_onAdminEndpoints() throws Exception {
        long standard = createTaxClass("Standard");
        long rate = createTaxRate(standard, "10", 1, null, null);
        mockMvc.perform(get("/backoffice/tax-rates/" + rate).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/tax-rates/paging").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(post("/backoffice/tax-rates").with(customer())
                .contentType(MediaType.APPLICATION_JSON).content(rateJson(standard, "0", 1L, null, null)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/backoffice/tax-rates/" + rate).with(customer())
                .contentType(MediaType.APPLICATION_JSON).content(rateJson(standard, "0", 1L, null, null)))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/backoffice/tax-rates/" + rate).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/tax-rates/" + rate).with(admin())).andExpect(jsonPath("$.rate").value(10.0));
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/backoffice/tax-rates/paging")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/backoffice/tax-rates/1")).andExpect(status().isUnauthorized());
    }
}
