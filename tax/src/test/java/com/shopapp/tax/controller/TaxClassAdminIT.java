package com.shopapp.tax.controller;

import static org.hamcrest.Matchers.contains;
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

class TaxClassAdminIT extends AbstractTaxIT {

    @Test
    void crud_happyPath() throws Exception {
        mockMvc.perform(post("/backoffice/tax-classes").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Standard\"}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString("/backoffice/tax-classes/1")))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Standard"));
        createTaxClass("Reduced");

        mockMvc.perform(get("/backoffice/tax-classes/1").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Standard"));
        mockMvc.perform(get("/backoffice/tax-classes").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", contains("Reduced", "Standard")));
        mockMvc.perform(get("/backoffice/tax-classes/paging?pageNo=0&pageSize=1").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.taxClassContent", hasSize(1)))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.isLast").value(false));

        mockMvc.perform(put("/backoffice/tax-classes/1").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Standard VAT\"}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/tax-classes/1").with(admin()))
            .andExpect(jsonPath("$.name").value("Standard VAT"));

        mockMvc.perform(delete("/backoffice/tax-classes/1").with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/tax-classes/1").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/backoffice/tax-classes/1").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void duplicateName_isBadRequest() throws Exception {
        createTaxClass("Standard");
        long reduced = createTaxClass("Reduced");
        mockMvc.perform(post("/backoffice/tax-classes").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Standard\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("Standard")));
        mockMvc.perform(put("/backoffice/tax-classes/" + reduced).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Standard\"}"))
            .andExpect(status().isBadRequest());
        // keeping its own name is fine
        mockMvc.perform(put("/backoffice/tax-classes/" + reduced).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Reduced\"}"))
            .andExpect(status().isNoContent());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"\"}", "{\"name\":\"   \"}"})
    void invalidName_isBadRequest(String body) throws Exception {
        mockMvc.perform(post("/backoffice/tax-classes").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void tooLongName_isBadRequest() throws Exception {
        mockMvc.perform(post("/backoffice/tax-classes").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + "x".repeat(256) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors[0]").value(containsString("name")));
    }

    @Test
    void deleteTaxClassInUse_isBadRequest_andKept() throws Exception {
        long id = createTaxClass("Standard");
        createTaxRate(id, "10", 1, null, null);
        mockMvc.perform(delete("/backoffice/tax-classes/" + id).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("in use")));
        mockMvc.perform(get("/backoffice/tax-classes/" + id).with(admin())).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"pageNo=-1", "pageSize=0", "pageSize=101", "pageNo=10000001", "pageNo=abc"})
    void pagingOutOfBounds_isBadRequest(String query) throws Exception {
        mockMvc.perform(get("/backoffice/tax-classes/paging?" + query).with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void customer_isForbidden() throws Exception {
        long id = createTaxClass("Standard");
        mockMvc.perform(get("/backoffice/tax-classes").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/tax-classes/" + id).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(post("/backoffice/tax-classes").with(customer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/backoffice/tax-classes/" + id).with(customer())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Hacked\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/backoffice/tax-classes/" + id).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/tax-classes/" + id).with(admin()))
            .andExpect(jsonPath("$.name").value("Standard"));
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/backoffice/tax-classes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/backoffice/tax-classes/paging")).andExpect(status().isUnauthorized());
    }
}
