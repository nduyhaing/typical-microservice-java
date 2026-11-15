package com.shopapp.location;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
public abstract class AbstractLocationIT {

    @Autowired
    protected MockMvc mockMvc;

    protected static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.subject("admin-user"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    protected static RequestPostProcessor customer(String userId) {
        return jwt().jwt(token -> token.subject(userId))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    protected static RequestPostProcessor customer() {
        return customer("customer-1");
    }

    /** A unique, at most 3 character code (country code2 is unique). */
    protected static String uniqueCode() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 3).toUpperCase();
    }

    protected static String uniqueName(String prefix) {
        return prefix + " " + UUID.randomUUID();
    }

    protected long createCountry(String name, String code2) throws Exception {
        String body = mockMvc.perform(post("/backoffice/countries").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code2":"%s","name":"%s","code3":"%s","isBillingEnabled":true,"isShippingEnabled":true,
                     "isCityEnabled":true,"isZipCodeEnabled":true,"isDistrictEnabled":false}
                    """.formatted(code2, name, code2)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    protected long createCountry() throws Exception {
        return createCountry(uniqueName("Country"), uniqueCode());
    }

    protected long createState(long countryId, String name) throws Exception {
        String body = mockMvc.perform(post("/backoffice/state-or-provinces").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name":"%s","code":"ST","type":"State","countryId":%d}
                    """.formatted(name, countryId)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }
}
