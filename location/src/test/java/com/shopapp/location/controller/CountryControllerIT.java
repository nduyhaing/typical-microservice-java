package com.shopapp.location.controller;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.location.AbstractLocationIT;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CountryControllerIT extends AbstractLocationIT {

    private static final String BASE = "/backoffice/countries";

    @Test
    void referenceData_isLoaded_andPublicOnStorefront() throws Exception {
        mockMvc.perform(get("/storefront/countries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(240))))
            .andExpect(jsonPath("$[?(@.code2 == 'VN')].name", hasItem("Viet Nam")))
            .andExpect(jsonPath("$[?(@.code2 == 'VN')].code3", hasItem("VNM")))
            .andExpect(jsonPath("$[?(@.code2 == 'US')].code3", hasItem("USA")))
            .andExpect(jsonPath("$[?(@.code2 == 'TT')].name", hasItem("Trinidad & Tobago")))
            .andExpect(jsonPath("$[*].name", not(hasItem(org.hamcrest.Matchers.containsString("&amp")))))
            // sorted by name
            .andExpect(jsonPath("$[0].name").value("Afghanistan"));
    }

    @Test
    void admin_createsReadsUpdatesDeletesCountry() throws Exception {
        String name = uniqueName("Freedonia");
        String code = uniqueCode();
        long id = createCountry(name, code);

        mockMvc.perform(get(BASE + "/" + id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.code2").value(code))
            .andExpect(jsonPath("$.isShippingEnabled").value(true))
            .andExpect(jsonPath("$.isDistrictEnabled").value(false));
        mockMvc.perform(get(BASE).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", hasItem(name)));

        String newName = uniqueName("Sylvania");
        mockMvc.perform(put(BASE + "/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code2":"%s","name":"%s","code3":"SYL","isBillingEnabled":false}
                    """.formatted(code, newName)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id).with(admin()))
            .andExpect(jsonPath("$.name").value(newName))
            .andExpect(jsonPath("$.code3").value("SYL"))
            .andExpect(jsonPath("$.isBillingEnabled").value(false));

        mockMvc.perform(delete(BASE + "/" + id).with(admin()))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id).with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void paging_returnsPage_andBoundsParameters() throws Exception {
        mockMvc.perform(get(BASE + "/paging").param("pageNo", "1").param("pageSize", "5").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.countryContent", hasSize(5)))
            .andExpect(jsonPath("$.pageNo").value(1))
            .andExpect(jsonPath("$.pageSize").value(5))
            .andExpect(jsonPath("$.totalElements", greaterThanOrEqualTo(240)))
            .andExpect(jsonPath("$.isLast").value(false));
        mockMvc.perform(get(BASE + "/paging").param("pageSize", "0").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/paging").param("pageSize", "101").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/paging").param("pageNo", "-1").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/paging").param("pageNo", "99999999999").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/paging").param("pageNo", "10000001").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/paging").param("pageNo", "10000000").param("pageSize", "100").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.countryContent", hasSize(0)));
    }

    @Test
    void customer_isForbidden_onEveryAdminEndpoint() throws Exception {
        long id = createCountry();
        mockMvc.perform(get(BASE).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/paging").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).with(customer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"ZZZ\",\"name\":\"Nope\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/" + id).with(customer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"ZZZ\",\"name\":\"Nope\"}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/" + id).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id).with(admin())).andExpect(status().isOk());
    }

    @Test
    void anonymous_isRefused() throws Exception {
        long id = createCountry();
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"ZZZ\",\"name\":\"Nope\"}"))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().is4xxClientError());
        mockMvc.perform(get(BASE + "/" + id).with(admin())).andExpect(status().isOk());
    }

    @Test
    void duplicateNameOrCode_isBadRequest() throws Exception {
        String name = uniqueName("Dup");
        String code = uniqueCode();
        long id = createCountry(name, code);
        long other = createCountry();

        // same name, other case
        mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"%s\",\"name\":\"%s\"}".formatted(uniqueCode(), name.toUpperCase())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("already")));
        mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"%s\",\"name\":\"%s\"}".formatted(code.toLowerCase(), uniqueName("X"))))
            .andExpect(status().isBadRequest());
        // a seeded code
        mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"VN\",\"name\":\"%s\"}".formatted(uniqueName("X"))))
            .andExpect(status().isBadRequest());
        // update another country to the same name / code
        mockMvc.perform(put(BASE + "/" + other).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"%s\",\"name\":\"%s\"}".formatted(uniqueCode(), name)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/" + other).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"%s\",\"name\":\"%s\"}".formatted(code, uniqueName("Y"))))
            .andExpect(status().isBadRequest());
        // updating itself with its own name/code is fine
        mockMvc.perform(put(BASE + "/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"%s\",\"name\":\"%s\"}".formatted(code, name)))
            .andExpect(status().isNoContent());
    }

    @Test
    void invalidBody_isBadRequest() throws Exception {
        String[] bodies = {
            "{\"code2\":\"AB\"}",
            "{\"code2\":\"AB\",\"name\":\"   \"}",
            "{\"name\":\"Nameless code\"}",
            "{\"code2\":\"ABCD\",\"name\":\"Long code\"}",
            "{\"code2\":\"AB\",\"name\":\"Long code3\",\"code3\":\"ABCD\"}",
            "{\"code2\":\"AB\",\"name\":\"" + "n".repeat(451) + "\"}",
            "not json",
        };
        for (String body : bodies) {
            mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        long id = createCountry();
        mockMvc.perform(put(BASE + "/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"AB\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void unknownCountry_isNotFound() throws Exception {
        mockMvc.perform(get(BASE + "/999999").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(put(BASE + "/999999").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"%s\",\"name\":\"%s\"}".formatted(uniqueCode(), uniqueName("X"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/999999").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/abc").with(admin())).andExpect(status().isBadRequest());
    }

    @Test
    void deleteCountryInUse_isBadRequest() throws Exception {
        long withState = createCountry();
        createState(withState, uniqueName("State"));
        mockMvc.perform(delete(BASE + "/" + withState).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("in use")));
        mockMvc.perform(get(BASE + "/" + withState).with(admin())).andExpect(status().isOk());

        long withAddress = createCountry();
        mockMvc.perform(post("/storefront/addresses").with(customer()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"contactName":"Ann","phone":"123","addressLine1":"1 Main St","city":"Town",
                     "zipCode":"1000","countryId":%d}
                    """.formatted(withAddress)))
            .andExpect(status().isOk());
        mockMvc.perform(delete(BASE + "/" + withAddress).with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/" + withAddress).with(admin())).andExpect(status().isOk());
    }

    @Test
    void storefrontCountries_includeNewCountry_andRefuseWrites() throws Exception {
        String name = uniqueName("Newland");
        createCountry(name, uniqueCode());
        mockMvc.perform(get("/storefront/countries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", hasItem(name)))
            .andExpect(jsonPath("$[*].id", everyItem(lessThanOrEqualTo(Integer.MAX_VALUE))));
        mockMvc.perform(post("/storefront/countries").with(customer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code2\":\"ZZZ\",\"name\":\"Nope\"}"))
            .andExpect(status().is4xxClientError());
    }
}
