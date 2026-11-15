package com.shopapp.tax.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Most specific rate wins: zip+state+country, zip+country, state+country, country only; none → 0.
 */
class TaxRateResolutionIT extends AbstractTaxIT {

    private static final long COUNTRY = 1;
    private static final long STATE = 2;

    private long standard;
    private long reduced;
    private long unused;

    @BeforeEach
    void rates() throws Exception {
        standard = createTaxClass("Standard");
        reduced = createTaxClass("Reduced");
        unused = createTaxClass("Unused");
        // created most specific first, so "first row found" (YAS) would not pass these tests
        createTaxRate(standard, "5", COUNTRY, STATE, "70000");
        createTaxRate(standard, "6", COUNTRY, null, "12345");
        createTaxRate(standard, "8", COUNTRY, STATE, null);
        createTaxRate(standard, "10", COUNTRY, null, null);
        createTaxRate(reduced, "7", COUNTRY, null, null);
        createTaxRate(standard, "20", 9, STATE, null); // other country, same state id
    }

    @ParameterizedTest(name = "state={0} zip={1} -> {2}")
    @CsvSource(nullValues = "-", value = {
        "-, -, 10.0",          // country only
        "2, -, 8.0",           // state beats country
        "2, 70000, 5.0",       // zip+state beats state
        "2, ' 70000 ', 5.0",   // zip is trimmed
        "2, 99999, 8.0",       // unknown zip falls back to the state
        "3, -, 10.0",          // unknown state falls back to the country
        "3, 70000, 10.0",      // zip rate bound to state 2 does not apply in state 3
        "3, 12345, 6.0",       // zip rate without state
        "-, 12345, 6.0",
        "2, 12345, 6.0",       // zip+country beats state+country
        "-, 70000, 10.0"
    })
    void taxPercent_mostSpecificWins(Long state, String zip, String expected) throws Exception {
        var request = get("/backoffice/tax-rates/tax-percent").with(customer())
            .param("taxClassId", String.valueOf(standard))
            .param("countryId", String.valueOf(COUNTRY));
        if (state != null) {
            request.param("stateOrProvinceId", String.valueOf(state));
        }
        if (zip != null) {
            request.param("zipCode", zip);
        }
        mockMvc.perform(request)
            .andExpect(status().isOk())
            .andExpect(content().string(expected));
    }

    @Test
    void taxPercent_isZero_whenNoRateApplies() throws Exception {
        mockMvc.perform(get("/backoffice/tax-rates/tax-percent").with(customer())
                .param("taxClassId", String.valueOf(standard)).param("countryId", "42"))
            .andExpect(status().isOk())
            .andExpect(content().string("0.0"));
        mockMvc.perform(get("/backoffice/tax-rates/tax-percent").with(customer())
                .param("taxClassId", String.valueOf(unused)).param("countryId", String.valueOf(COUNTRY)))
            .andExpect(content().string("0.0"));
        mockMvc.perform(get("/backoffice/tax-rates/tax-percent").with(admin())
                .param("taxClassId", "999").param("countryId", String.valueOf(COUNTRY)))
            .andExpect(content().string("0.0"));
    }

    @Test
    void batch_returnsMostSpecificRatePerClass_andOmitsClassesWithoutRate() throws Exception {
        mockMvc.perform(get("/backoffice/tax-rates/location-based-batch").with(customer())
                .param("taxClassIds", standard + "," + reduced + "," + unused)
                .param("countryId", String.valueOf(COUNTRY))
                .param("stateOrProvinceId", String.valueOf(STATE))
                .param("zipCode", "70000"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[?(@.taxClassId == " + standard + ")].rate").value(5.0))
            .andExpect(jsonPath("$[?(@.taxClassId == " + standard + ")].zipCode").value("70000"))
            .andExpect(jsonPath("$[?(@.taxClassId == " + reduced + ")].rate").value(7.0))
            .andExpect(jsonPath("$[*].taxClassId", containsInAnyOrder((int) standard, (int) reduced)));

        mockMvc.perform(get("/backoffice/tax-rates/location-based-batch").with(customer())
                .param("taxClassIds", String.valueOf(standard), String.valueOf(standard))
                .param("countryId", String.valueOf(COUNTRY))
                .param("stateOrProvinceId", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].rate").value(10.0));

        mockMvc.perform(get("/backoffice/tax-rates/location-based-batch").with(customer())
                .param("taxClassIds", String.valueOf(standard))
                .param("countryId", "42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "/backoffice/tax-rates/tax-percent?countryId=1",
        "/backoffice/tax-rates/tax-percent?taxClassId=1",
        "/backoffice/tax-rates/tax-percent?taxClassId=x&countryId=1",
        "/backoffice/tax-rates/tax-percent?taxClassId=1&countryId=1&zipCode=12345678901234567890123456",
        "/backoffice/tax-rates/location-based-batch?countryId=1",
        "/backoffice/tax-rates/location-based-batch?taxClassIds=&countryId=1",
        "/backoffice/tax-rates/location-based-batch?taxClassIds=1",
        "/backoffice/tax-rates/location-based-batch?taxClassIds=1,y&countryId=1"
    })
    void missingOrInvalidParams_isBadRequest(String url) throws Exception {
        mockMvc.perform(get(url).with(customer())).andExpect(status().isBadRequest());
    }

    @Test
    void batch_withMoreThan100Classes_isBadRequest() throws Exception {
        String ids = LongStream.rangeClosed(1, 101).mapToObj(String::valueOf).collect(Collectors.joining(","));
        mockMvc.perform(get("/backoffice/tax-rates/location-based-batch").with(customer())
                .param("taxClassIds", ids).param("countryId", "1"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/backoffice/tax-rates/tax-percent")
                .param("taxClassId", String.valueOf(standard)).param("countryId", "1"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/backoffice/tax-rates/location-based-batch")
                .param("taxClassIds", String.valueOf(standard)).param("countryId", "1"))
            .andExpect(status().isUnauthorized());
    }
}
