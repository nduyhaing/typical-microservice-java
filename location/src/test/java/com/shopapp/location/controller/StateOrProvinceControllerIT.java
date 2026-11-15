package com.shopapp.location.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.location.AbstractLocationIT;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class StateOrProvinceControllerIT extends AbstractLocationIT {

    private static final String BASE = "/backoffice/state-or-provinces";

    private long vietNamId() throws Exception {
        String body = mockMvc.perform(get("/storefront/countries")).andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.code2 == 'VN')].id");
        return ids.getFirst().longValue();
    }

    private long stateId(long countryId, String name) throws Exception {
        String body = mockMvc.perform(get("/storefront/state-or-provinces/" + countryId))
            .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(body, "$[?(@.name == '" + name + "')].id");
        return ids.getFirst().longValue();
    }

    @Test
    void storefront_listsSeededProvincesAndDistricts_publicly() throws Exception {
        long vn = vietNamId();
        mockMvc.perform(get("/storefront/state-or-provinces/" + vn))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(63)))
            .andExpect(jsonPath("$[*].name", hasItem("Hà Nội")))
            .andExpect(jsonPath("$[0].countryId").value(vn));
        long hanoi = stateId(vn, "Hà Nội");
        mockMvc.perform(get("/storefront/district/" + hanoi))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", hasItem("Ba Đình")))
            .andExpect(jsonPath("$[*].name", hasItem("Hoàn Kiếm")))
            .andExpect(jsonPath("$[0].id").isNumber());
        long hcm = stateId(vn, "Hồ Chí Minh");
        mockMvc.perform(get("/storefront/district/" + hcm))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThan(10))));
        // unknown ids: empty lists
        mockMvc.perform(get("/storefront/state-or-provinces/999999"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/storefront/district/999999"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/storefront/district/abc")).andExpect(status().isBadRequest());
    }

    @Test
    void admin_districtLookup_isAdminOnly() throws Exception {
        long hanoi = stateId(vietNamId(), "Hà Nội");
        mockMvc.perform(get("/backoffice/district/" + hanoi).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", hasItem("Ba Đình")));
        mockMvc.perform(get("/backoffice/district/" + hanoi).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/district/" + hanoi)).andExpect(status().isUnauthorized());
    }

    @Test
    void admin_createsReadsUpdatesDeletesState() throws Exception {
        long country = createCountry();
        String name = uniqueName("Province");
        long id = createState(country, name);

        mockMvc.perform(get(BASE + "/" + id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.code").value("ST"))
            .andExpect(jsonPath("$.type").value("State"))
            .andExpect(jsonPath("$.countryId").value(country));
        mockMvc.perform(get(BASE).param("countryId", String.valueOf(country)).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].name").value(name));
        mockMvc.perform(get("/storefront/state-or-provinces/" + country))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value(name));

        String newName = uniqueName("Region");
        mockMvc.perform(put(BASE + "/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"code\":\"RG\",\"type\":\"Region\",\"countryId\":%d}"
                    .formatted(newName, country)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id).with(admin()))
            .andExpect(jsonPath("$.name").value(newName))
            .andExpect(jsonPath("$.code").value("RG"))
            .andExpect(jsonPath("$.type").value("Region"));

        mockMvc.perform(get(BASE + "/state-country-names").param("stateOrProvinceIds", String.valueOf(id))
                .with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].stateOrProvinceId").value(id))
            .andExpect(jsonPath("$[0].stateOrProvinceName").value(newName))
            .andExpect(jsonPath("$[0].countryName").isString());

        mockMvc.perform(delete(BASE + "/" + id).with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id).with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void paging_filtersByCountry_andBoundsParameters() throws Exception {
        long country = createCountry();
        createState(country, "B " + uniqueName("S"));
        createState(country, "A " + uniqueName("S"));
        createState(country, "C " + uniqueName("S"));
        mockMvc.perform(get(BASE + "/paging").param("countryId", String.valueOf(country))
                .param("pageNo", "0").param("pageSize", "2").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stateOrProvinceContent", hasSize(2)))
            .andExpect(jsonPath("$.stateOrProvinceContent[0].name").value(containsString("A ")))
            .andExpect(jsonPath("$.totalElements").value(3))
            .andExpect(jsonPath("$.totalPages").value(2))
            .andExpect(jsonPath("$.isLast").value(false));
        // without a country: all states
        mockMvc.perform(get(BASE + "/paging").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements", greaterThan(63)));
        mockMvc.perform(get(BASE + "/paging").param("pageSize", "101").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/paging").param("pageNo", "-1").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void stateCountryNames_boundsIds() throws Exception {
        mockMvc.perform(get(BASE + "/state-country-names").with(admin())).andExpect(status().isBadRequest());
        StringBuilder ids = new StringBuilder("1");
        for (int i = 2; i <= 101; i++) {
            ids.append(',').append(i);
        }
        mockMvc.perform(get(BASE + "/state-country-names").param("stateOrProvinceIds", ids.toString())
                .with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void customerAndAnonymous_areRefused() throws Exception {
        long country = createCountry();
        long id = createState(country, uniqueName("Guarded"));
        String body = "{\"name\":\"Nope\",\"countryId\":%d}".formatted(country);
        mockMvc.perform(get(BASE).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/paging").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + id).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/state-country-names").param("stateOrProvinceIds", "1").with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).with(customer()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(put(BASE + "/" + id).with(customer()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete(BASE + "/" + id).with(customer())).andExpect(status().isForbidden());

        mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().is4xxClientError());
        mockMvc.perform(get(BASE + "/" + id).with(admin())).andExpect(status().isOk());
    }

    @Test
    void duplicateNameInSameCountry_isBadRequest_otherCountryIsFine() throws Exception {
        long country = createCountry();
        String name = uniqueName("Twin");
        long id = createState(country, name);
        mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"countryId\":%d}".formatted(name.toLowerCase(), country)))
            .andExpect(status().isBadRequest());
        long other = createState(country, uniqueName("Other"));
        mockMvc.perform(put(BASE + "/" + other).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"countryId\":%d}".formatted(name, country)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"%s\",\"countryId\":%d}".formatted(name, country)))
            .andExpect(status().isNoContent());
        createState(createCountry(), name);
    }

    @Test
    void invalidBody_orUnknownCountry_isRefused() throws Exception {
        long country = createCountry();
        String[] bodies = {
            "{\"countryId\":%d}".formatted(country),
            "{\"name\":\"  \",\"countryId\":%d}".formatted(country),
            "{\"name\":\"No country\"}",
            "{\"name\":\"%s\",\"countryId\":%d}".formatted("n".repeat(451), country),
            "{\"name\":\"Long code\",\"code\":\"%s\",\"countryId\":%d}".formatted("c".repeat(256), country),
            "{\"name\":\"Long type\",\"type\":\"%s\",\"countryId\":%d}".formatted("t".repeat(256), country),
        };
        for (String body : bodies) {
            mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(BASE).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Orphan\",\"countryId\":999999}"))
            .andExpect(status().isBadRequest());
        long id = createState(country, uniqueName("Valid"));
        mockMvc.perform(put(BASE + "/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/999999").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Ghost\",\"countryId\":%d}".formatted(country)))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/999999").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void deleteStateInUse_isBadRequest() throws Exception {
        // a seeded province with districts
        long hanoi = stateId(vietNamId(), "Hà Nội");
        mockMvc.perform(delete(BASE + "/" + hanoi).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("in use")));
        mockMvc.perform(get(BASE + "/" + hanoi).with(admin())).andExpect(status().isOk());

        // a state used by an address
        long country = createCountry();
        long state = createState(country, uniqueName("Used"));
        mockMvc.perform(post("/storefront/addresses").with(customer()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"contactName":"Bob","phone":"555","addressLine1":"2 Side St","city":"City",
                     "zipCode":"2000","countryId":%d,"stateOrProvinceId":%d}
                    """.formatted(country, state)))
            .andExpect(status().isOk());
        mockMvc.perform(delete(BASE + "/" + state).with(admin())).andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/" + state).with(admin())).andExpect(status().isOk());
    }
}
