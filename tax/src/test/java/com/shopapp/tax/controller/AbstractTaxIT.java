package com.shopapp.tax.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.tax.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
abstract class AbstractTaxIT {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("TRUNCATE tax_rate, tax_class RESTART IDENTITY");
    }

    protected static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    protected static RequestPostProcessor customer() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    protected long createTaxClass(String name) throws Exception {
        String body = mockMvc.perform(post("/backoffice/tax-classes").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected long createTaxRate(long taxClassId, String rate, long countryId, Long stateId, String zipCode)
        throws Exception {
        String body = mockMvc.perform(post("/backoffice/tax-rates").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(rateJson(taxClassId, rate, countryId, stateId, zipCode)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected static String rateJson(Long taxClassId, String rate, Long countryId, Long stateId, String zipCode) {
        return "{\"taxClassId\":" + taxClassId + ",\"rate\":" + rate + ",\"countryId\":" + countryId
            + ",\"stateOrProvinceId\":" + stateId
            + ",\"zipCode\":" + (zipCode == null ? "null" : "\"" + zipCode + "\"") + "}";
    }
}
