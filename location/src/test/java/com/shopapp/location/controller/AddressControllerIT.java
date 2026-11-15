package com.shopapp.location.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

class AddressControllerIT extends AbstractLocationIT {

    private static final String BASE = "/storefront/addresses";

    private long vn;
    private long hanoi;
    private long baDinh;
    private long hcm;

    @BeforeEach
    void seedIds() throws Exception {
        String countries = mockMvc.perform(get("/storefront/countries")).andReturn().getResponse()
            .getContentAsString();
        vn = first(JsonPath.read(countries, "$[?(@.code2 == 'VN')].id"));
        String states = mockMvc.perform(get("/storefront/state-or-provinces/" + vn)).andReturn().getResponse()
            .getContentAsString();
        hanoi = first(JsonPath.read(states, "$[?(@.name == 'Hà Nội')].id"));
        hcm = first(JsonPath.read(states, "$[?(@.name == 'Hồ Chí Minh')].id"));
        String districts = mockMvc.perform(get("/storefront/district/" + hanoi)).andReturn().getResponse()
            .getContentAsString();
        baDinh = first(JsonPath.read(districts, "$[?(@.name == 'Ba Đình')].id"));
    }

    private static long first(List<Number> ids) {
        return ids.getFirst().longValue();
    }

    private String body(String contactName, Long country, Long state, Long district) {
        return """
            {"contactName":"%s","phone":"0901234567","addressLine1":"12 Kim Ma","addressLine2":"Floor 3",
             "city":"Ha Noi","zipCode":"100000","countryId":%s,"stateOrProvinceId":%s,"districtId":%s}
            """.formatted(contactName, country, state, district);
    }

    private long create(RequestPostProcessor user, String contactName) throws Exception {
        String response = mockMvc.perform(post(BASE).with(user).contentType(MediaType.APPLICATION_JSON)
                .content(body(contactName, vn, hanoi, baDinh)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    @Test
    void customer_createsReadsUpdatesDeletesOwnAddress() throws Exception {
        mockMvc.perform(post(BASE).with(customer("alice")).contentType(MediaType.APPLICATION_JSON)
                .content(body("Alice", vn, hanoi, baDinh)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.contactName").value("Alice"))
            .andExpect(jsonPath("$.countryId").value(vn))
            .andExpect(jsonPath("$.stateOrProvinceId").value(hanoi))
            .andExpect(jsonPath("$.districtId").value(baDinh))
            .andExpect(jsonPath("$.zipCode").value("100000"));
        long id = create(customer("alice"), "Alice 2");

        mockMvc.perform(get(BASE + "/" + id).with(customer("alice")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contactName").value("Alice 2"))
            .andExpect(jsonPath("$.phone").value("0901234567"))
            .andExpect(jsonPath("$.addressLine1").value("12 Kim Ma"))
            .andExpect(jsonPath("$.addressLine2").value("Floor 3"))
            .andExpect(jsonPath("$.city").value("Ha Noi"))
            .andExpect(jsonPath("$.countryName").value("Viet Nam"))
            .andExpect(jsonPath("$.stateOrProvinceName").value("Hà Nội"))
            .andExpect(jsonPath("$.districtName").value("Ba Đình"));

        mockMvc.perform(put(BASE + "/" + id).with(customer("alice")).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"contactName":"Alice New","phone":"0911","addressLine1":"1 Le Loi","city":"HCMC",
                     "zipCode":"700000","countryId":%d,"stateOrProvinceId":%d}
                    """.formatted(vn, hcm)))
            .andExpect(status().isNoContent());
        mockMvc.perform(get(BASE + "/" + id).with(customer("alice")))
            .andExpect(jsonPath("$.contactName").value("Alice New"))
            .andExpect(jsonPath("$.addressLine2").isEmpty())
            .andExpect(jsonPath("$.stateOrProvinceName").value("Hồ Chí Minh"))
            .andExpect(jsonPath("$.districtId").isEmpty())
            .andExpect(jsonPath("$.districtName").isEmpty());

        mockMvc.perform(delete(BASE + "/" + id).with(customer("alice"))).andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + id).with(customer("alice"))).andExpect(status().isNotFound());
    }

    @Test
    void addressOfAnotherUser_isNotFound_butAdminMayReadIt() throws Exception {
        long id = create(customer("bob"), "Bob");
        String update = body("Mallory", vn, hanoi, baDinh);

        mockMvc.perform(get(BASE + "/" + id).with(customer("mallory"))).andExpect(status().isNotFound());
        mockMvc.perform(put(BASE + "/" + id).with(customer("mallory")).contentType(MediaType.APPLICATION_JSON)
                .content(update))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/" + id).with(customer("mallory"))).andExpect(status().isNotFound());
        mockMvc.perform(get(BASE).param("ids", String.valueOf(id)).with(customer("mallory")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get(BASE + "/" + id).with(customer("bob")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contactName").value("Bob"));
        mockMvc.perform(get(BASE + "/" + id).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contactName").value("Bob"));
    }

    @Test
    void listByIds_returnsOnlyOwnAddresses() throws Exception {
        long a1 = create(customer("carol"), "Carol 1");
        long a2 = create(customer("carol"), "Carol 2");
        long other = create(customer("dave"), "Dave");
        mockMvc.perform(get(BASE).param("ids", a1 + "," + a2 + "," + other + ",999999").with(customer("carol")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[*].contactName", containsInAnyOrder("Carol 1", "Carol 2")))
            .andExpect(jsonPath("$[0].countryName").value("Viet Nam"));
        mockMvc.perform(get(BASE).param("ids", a1 + "," + other).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)));
        mockMvc.perform(get(BASE).with(customer("carol"))).andExpect(status().isBadRequest());
        StringBuilder ids = new StringBuilder("1");
        for (int i = 2; i <= 101; i++) {
            ids.append(',').append(i);
        }
        mockMvc.perform(get(BASE).param("ids", ids.toString()).with(customer("carol")))
            .andExpect(status().isBadRequest());
    }

    @Test
    void anonymous_isRefused() throws Exception {
        long id = create(customer("erin"), "Erin");
        mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE).param("ids", String.valueOf(id))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body("Anon", vn, hanoi, baDinh)))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(put(BASE + "/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body("Anon", vn, hanoi, baDinh)))
            .andExpect(status().is4xxClientError());
        mockMvc.perform(delete(BASE + "/" + id)).andExpect(status().is4xxClientError());
        mockMvc.perform(get(BASE + "/" + id).with(customer("erin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.contactName").value("Erin"));
    }

    @Test
    void inconsistentOrUnknownLocation_isBadRequest() throws Exception {
        long otherCountry = createCountry();
        long otherState = createState(otherCountry, uniqueName("Elsewhere"));
        String[] bodies = {
            body("X", 999999L, null, null),
            body("X", vn, 999999L, null),
            body("X", vn, hanoi, 999999L),
            // state of another country
            body("X", vn, otherState, null),
            // district of another state
            body("X", vn, hcm, baDinh),
            // district without a state
            body("X", vn, null, baDinh),
        };
        for (String body : bodies) {
            mockMvc.perform(post(BASE).with(customer()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        long id = create(customer(), "Valid");
        mockMvc.perform(put(BASE + "/" + id).with(customer()).contentType(MediaType.APPLICATION_JSON)
                .content(body("X", vn, hcm, baDinh)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/" + id).with(customer()))
            .andExpect(jsonPath("$.contactName").value("Valid"))
            .andExpect(jsonPath("$.districtName").value("Ba Đình"));
    }

    @Test
    void invalidBody_isBadRequest() throws Exception {
        String[] bodies = {
            body("", vn, hanoi, baDinh),
            body("n".repeat(451), vn, hanoi, baDinh),
            body("No country", null, null, null),
            "{\"contactName\":\"No phone\",\"addressLine1\":\"1 St\",\"countryId\":%d}".formatted(vn),
            "{\"contactName\":\"No line\",\"phone\":\"1\",\"countryId\":%d}".formatted(vn),
            "{\"contactName\":\"Long phone\",\"phone\":\"%s\",\"addressLine1\":\"1 St\",\"countryId\":%d}"
                .formatted("1".repeat(26), vn),
            "{\"contactName\":\"Long zip\",\"phone\":\"1\",\"addressLine1\":\"1 St\",\"zipCode\":\"%s\",\"countryId\":%d}"
                .formatted("1".repeat(26), vn),
            "{\"contactName\":\"Long city\",\"phone\":\"1\",\"addressLine1\":\"1 St\",\"city\":\"%s\",\"countryId\":%d}"
                .formatted("c".repeat(451), vn),
        };
        for (String body : bodies) {
            mockMvc.perform(post(BASE).with(customer()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    void unknownAddress_isNotFound() throws Exception {
        mockMvc.perform(get(BASE + "/999999").with(customer())).andExpect(status().isNotFound());
        mockMvc.perform(put(BASE + "/999999").with(customer()).contentType(MediaType.APPLICATION_JSON)
                .content(body("Ghost", vn, hanoi, baDinh)))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/999999").with(customer())).andExpect(status().isNotFound());
    }
}
