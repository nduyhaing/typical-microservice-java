package com.shopapp.webhook.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.webhook.support.AbstractWebhookIT;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class WebhookAdminIT extends AbstractWebhookIT {

    private static String body(String url, String secret, String events) {
        return "{\"payloadUrl\":\"" + url + "\"," + (secret == null ? "" : "\"secret\":\"" + secret + "\",")
            + "\"contentType\":\"application/json\",\"isActive\":true,\"events\":[" + events + "]}";
    }

    @Test
    void crud_happyPath_secretIsNeverReturned() throws Exception {
        String created = mockMvc.perform(post("/backoffice/webhooks").with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("https://public.test/shop", SECRET, "{\"id\":1},{\"id\":2}")))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString("/backoffice/webhooks/1")))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.payloadUrl").value("https://public.test/shop"))
            .andExpect(jsonPath("$.contentType").value("application/json"))
            .andExpect(jsonPath("$.isActive").value(true))
            .andExpect(jsonPath("$.maskedSecret").value("********cdef"))
            .andExpect(jsonPath("$.secret").doesNotExist())
            .andExpect(jsonPath("$.events[*].name").value(org.hamcrest.Matchers.contains(
                "ON_PRODUCT_UPDATED", "ON_ORDER_CREATED")))
            .andReturn().getResponse().getContentAsString();
        assertThat(created).doesNotContain(SECRET);

        String detail = mockMvc.perform(get("/backoffice/webhooks/1").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maskedSecret").value("********cdef"))
            .andReturn().getResponse().getContentAsString();
        assertThat(detail).doesNotContain(SECRET);
        String list = mockMvc.perform(get("/backoffice/webhooks").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContain(SECRET);
        mockMvc.perform(get("/backoffice/webhooks/paging?pageNo=0&pageSize=5").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.webhooks", hasSize(1)))
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.isLast").value(true));

        // update without a secret keeps it; events are replaced
        mockMvc.perform(put("/backoffice/webhooks/1").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"payloadUrl\":\"https://public.test/v2\",\"isActive\":false,\"events\":[{\"id\":3}]}"))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/webhooks/1").with(admin()))
            .andExpect(jsonPath("$.payloadUrl").value("https://public.test/v2"))
            .andExpect(jsonPath("$.isActive").value(false))
            .andExpect(jsonPath("$.events[*].name").value(org.hamcrest.Matchers.contains("ON_ORDER_STATUS_UPDATED")));
        assertThat(jdbc.queryForObject("SELECT secret FROM webhook WHERE id = 1", String.class)).isEqualTo(SECRET);

        // a new secret replaces it
        mockMvc.perform(put("/backoffice/webhooks/1").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(body("https://public.test/v2", "another-secret-9876", "")))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/webhooks/1").with(admin()))
            .andExpect(jsonPath("$.maskedSecret").value("********9876"))
            .andExpect(jsonPath("$.events", hasSize(0)));

        mockMvc.perform(delete("/backoffice/webhooks/1").with(admin())).andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/webhooks/1").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(delete("/backoffice/webhooks/1").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void events_areSeeded() throws Exception {
        mockMvc.perform(get("/backoffice/events").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.contains(
                "ON_PRODUCT_UPDATED", "ON_ORDER_CREATED", "ON_ORDER_STATUS_UPDATED")))
            .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void everyEndpoint_isAdminOnly() throws Exception {
        long id = createWebhook("https://public.test/x", 1);
        MockHttpServletRequestBuilder[] requests = {
            get("/backoffice/webhooks"), get("/backoffice/webhooks/paging"), get("/backoffice/webhooks/" + id),
            get("/backoffice/events"), get("/backoffice/webhooks/" + id + "/notifications"),
            post("/backoffice/webhooks/" + id + "/test"),
            post("/backoffice/webhooks").contentType(MediaType.APPLICATION_JSON)
                .content(body("https://public.test/y", SECRET, "")),
            put("/backoffice/webhooks/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(body("https://public.test/y", SECRET, "")),
            delete("/backoffice/webhooks/" + id)};
        for (MockHttpServletRequestBuilder request : requests) {
            mockMvc.perform(request).andExpect(status().isUnauthorized());
            mockMvc.perform(request.with(customer())).andExpect(status().isForbidden());
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook", Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://localhost/", "http://localhost:8080/", "http://127.0.0.1/", "http://2130706433/",
        "http://0.0.0.0/", "http://[::1]/", "http://[::ffff:127.0.0.1]/", "http://10.0.0.1/",
        "http://172.16.0.1/", "http://192.168.1.10/", "http://169.254.169.254/", "http://169.254.169.254/latest/",
        "http://100.64.0.1/", "http://224.0.0.1/", "http://[fd00::1]/", "http://[fe80::1]/",
        "ftp://public.test/", "file:///etc/passwd", "gopher://public.test/", "http://user:pass@public.test/",
        "public.test/hook", "http://nowhere.invalid/"})
    void unsafePayloadUrls_areRefusedAtRegistration(String url) throws Exception {
        mockMvc.perform(post("/backoffice/webhooks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(body(url, SECRET, "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("Payload URL")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook", Integer.class)).isZero();
    }

    @Test
    void unsafeUrl_isRefusedOnUpdateToo() throws Exception {
        long id = createWebhook("https://public.test/x", 1);
        mockMvc.perform(put("/backoffice/webhooks/" + id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(body("http://169.254.169.254/", null, "")))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT payload_url FROM webhook", String.class))
            .isEqualTo("https://public.test/x");
    }

    @Test
    void invalidInput_isBadRequest() throws Exception {
        String[] bodies = {
            body("https://public.test/x", null, ""),                      // no secret on create
            body("https://public.test/x", "short", ""),                   // secret under 16
            body("https://public.test/x", SECRET, "{\"id\":99}"),        // unknown event
            body("", SECRET, ""),                                          // no URL
            "{\"payloadUrl\":\"https://public.test/x\",\"secret\":\"" + SECRET
                + "\",\"contentType\":\"application/xml\"}"};
        for (String content : bodies) {
            mockMvc.perform(post("/backoffice/webhooks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                    .content(content))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/backoffice/webhooks/paging?pageSize=101").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/webhooks/paging?pageNo=-1").with(admin()))
            .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM webhook", Integer.class)).isZero();
    }

    @Test
    void atMostFiftyWebhooks() throws Exception {
        jdbc.update("INSERT INTO webhook (payload_url, content_type, secret, is_active) "
            + "SELECT 'https://public.test/' || g, 'application/json', ?, false FROM generate_series(1, 50) g", SECRET);
        mockMvc.perform(post("/backoffice/webhooks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(body("https://public.test/x", SECRET, "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("50")));
    }

    @Test
    void notificationsOfAMissingWebhook_isNotFound() throws Exception {
        mockMvc.perform(get("/backoffice/webhooks/42/notifications").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(post("/backoffice/webhooks/42/test").with(admin())).andExpect(status().isNotFound());
    }
}
