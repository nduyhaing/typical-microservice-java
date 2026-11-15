package com.shopapp.customer.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.customer.PostgresContainerSupport;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Saved addresses (customer ↔ location). Location is a JDK HttpServer stub that, like the real service, keeps the
 * owner of each address (the relayed token) and hides other users' addresses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
class UserAddressControllerIT {

    private static final String BASE = "/storefront/user-address";
    private static final AtomicBoolean LOCATION_DOWN = new AtomicBoolean(false);
    private static final AtomicLong NEXT_ID = new AtomicLong(100);
    private static final AtomicReference<String> LAST_AUTHORIZATION = new AtomicReference<>();
    /** address id → (owner token, posted body). */
    private static final Map<Long, String[]> ADDRESSES = new ConcurrentHashMap<>();
    private static final HttpServer LOCATION = location();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private Keycloak keycloak;

    private static HttpServer location() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/location/storefront/addresses", UserAddressControllerIT::handleAddresses);
            server.start();
            return server;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static void handleAddresses(HttpExchange exchange) throws IOException {
        String owner = exchange.getRequestHeaders().getFirst("Authorization");
        LAST_AUTHORIZATION.set(owner);
        if (LOCATION_DOWN.get()) {
            respond(exchange, 503, "{}");
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        String tail = path.substring("/location/storefront/addresses".length());
        if (tail.isEmpty() && "POST".equals(method)) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"countryId\":999")) {
                respond(exchange, 400, "{\"detail\":\"Country 999 is not found\"}");
                return;
            }
            long id = NEXT_ID.getAndIncrement();
            ADDRESSES.put(id, new String[] {owner, body});
            respond(exchange, 200, withId(id, body));
        } else if (tail.isEmpty() && "GET".equals(method)) {
            String ids = exchange.getRequestURI().getQuery().replace("ids=", "");
            String list = Arrays.stream(ids.split("[,&]"))
                .map(Long::valueOf)
                .filter(id -> ADDRESSES.containsKey(id) && ADDRESSES.get(id)[0].equals(owner))
                .map(id -> withId(id, ADDRESSES.get(id)[1]))
                .collect(Collectors.joining(","));
            respond(exchange, 200, "[" + list + "]");
        } else {
            long id = Long.parseLong(tail.substring(1));
            String[] address = ADDRESSES.get(id);
            if (address == null || !address[0].equals(owner)) {
                respond(exchange, 404, "{\"detail\":\"Address not found\"}");
            } else if ("DELETE".equals(method)) {
                ADDRESSES.remove(id);
                respond(exchange, 200, "");
            } else {
                respond(exchange, 200, withId(id, address[1]));
            }
        }
    }

    private static String withId(long id, String body) {
        return "{\"id\":" + id + ",\"countryName\":\"Viet Nam\"," + body.trim().substring(1);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    @DynamicPropertySource
    static void serviceUrls(DynamicPropertyRegistry registry) {
        registry.add("shopapp.services.location",
            () -> "http://127.0.0.1:" + LOCATION.getAddress().getPort() + "/location");
    }

    @AfterAll
    static void stopLocation() {
        LOCATION.stop(0);
    }

    @BeforeEach
    void reset() {
        LOCATION_DOWN.set(false);
        LAST_AUTHORIZATION.set(null);
        ADDRESSES.clear();
        jdbc.execute("TRUNCATE user_address RESTART IDENTITY");
    }

    private static RequestPostProcessor customer(String id) {
        return jwt().jwt(token -> token.subject(id).tokenValue("token-" + id))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private static String address(String contactName) {
        return """
            {"contactName":"%s","phone":"0123456789","addressLine1":"1 Main St","addressLine2":null,
             "city":"Ho Chi Minh City","zipCode":"700000","districtId":null,"stateOrProvinceId":null,"countryId":1}"""
            .formatted(contactName);
    }

    private long create(String user, String contactName) throws Exception {
        String response = mockMvc.perform(post(BASE).with(customer(user))
                .contentType(MediaType.APPLICATION_JSON).content(address(contactName)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.addressGetVm.id")).longValue();
    }

    private int links(String user) {
        return jdbc.queryForObject("select count(*) from user_address where user_id = ?", Integer.class, user);
    }

    @Test
    void create_firstAddressIsDefault_laterOnesAreNot() throws Exception {
        mockMvc.perform(post(BASE).with(customer("u1"))
                .contentType(MediaType.APPLICATION_JSON).content(address("Home")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value("u1"))
            .andExpect(jsonPath("$.isActive").value(true))
            .andExpect(jsonPath("$.addressGetVm.contactName").value("Home"));
        assertThat(LAST_AUTHORIZATION.get()).isEqualTo("Bearer token-u1");

        mockMvc.perform(post(BASE).with(customer("u1"))
                .contentType(MediaType.APPLICATION_JSON).content(address("Office")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void list_returnsOnlyOwnAddresses_defaultFirst() throws Exception {
        create("u1", "Home");
        create("u1", "Office");
        create("u2", "Other user");

        mockMvc.perform(get(BASE).with(customer("u1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].contactName").value("Home"))
            .andExpect(jsonPath("$[0].isActive").value(true))
            .andExpect(jsonPath("$[0].countryName").value("Viet Nam"))
            .andExpect(jsonPath("$[1].contactName").value("Office"))
            .andExpect(jsonPath("$[1].isActive").value(false));

        mockMvc.perform(get(BASE).with(customer("u2")))
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].contactName").value("Other user"));

        mockMvc.perform(get(BASE).with(customer("u3")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void chooseDefault_switchesTheDefault() throws Exception {
        create("u1", "Home");
        long office = create("u1", "Office");

        mockMvc.perform(put(BASE + "/" + office).with(customer("u1"))).andExpect(status().isOk());

        mockMvc.perform(get(BASE).with(customer("u1")))
            .andExpect(jsonPath("$[0].contactName").value("Office"))
            .andExpect(jsonPath("$[0].isActive").value(true))
            .andExpect(jsonPath("$[1].isActive").value(false));
        mockMvc.perform(get(BASE + "/default-address").with(customer("u1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(office))
            .andExpect(jsonPath("$.contactName").value("Office"));
        assertThat(jdbc.queryForObject("select count(*) from user_address where user_id = 'u1' and is_active",
            Integer.class)).isEqualTo(1);
    }

    @Test
    void defaultAddress_noneSaved_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/default-address").with(customer("u1"))).andExpect(status().isNotFound());
    }

    @Test
    void delete_removesLinkAndLocationAddress_andPromotesAnotherDefault() throws Exception {
        long home = create("u1", "Home");
        long office = create("u1", "Office");

        mockMvc.perform(delete(BASE + "/" + home).with(customer("u1"))).andExpect(status().isOk());

        assertThat(ADDRESSES).doesNotContainKey(home);
        assertThat(links("u1")).isEqualTo(1);
        mockMvc.perform(get(BASE + "/default-address").with(customer("u1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(office));
    }

    @Test
    void otherUsersAddress_updateAndDelete_return404_andChangeNothing() throws Exception {
        long home = create("u1", "Home");
        create("u2", "Mine");

        mockMvc.perform(put(BASE + "/" + home).with(customer("u2"))).andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/" + home).with(customer("u2"))).andExpect(status().isNotFound());
        mockMvc.perform(put(BASE + "/987654").with(customer("u1"))).andExpect(status().isNotFound());

        assertThat(ADDRESSES).containsKey(home);
        assertThat(links("u1")).isEqualTo(1);
        mockMvc.perform(get(BASE).with(customer("u1")))
            .andExpect(jsonPath("$[0].isActive").value(true));
        mockMvc.perform(get(BASE).with(customer("u2")))
            .andExpect(jsonPath("$[0].isActive").value(true));
    }

    @Test
    void anonymous_returns401() throws Exception {
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/default-address")).andExpect(status().isUnauthorized());
    }

    @Test
    void create_invalidBody_returns400_withoutCallingLocation() throws Exception {
        mockMvc.perform(post(BASE).with(customer("u1"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"contactName\":\"\",\"countryId\":1}"))
            .andExpect(status().isBadRequest());
        assertThat(LAST_AUTHORIZATION.get()).isNull();
    }

    @Test
    void create_locationRefusesBody_returns400_andNoLink() throws Exception {
        mockMvc.perform(post(BASE).with(customer("u1")).contentType(MediaType.APPLICATION_JSON)
                .content(address("Home").replace("\"countryId\":1", "\"countryId\":999")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value("Country 999 is not found"));
        assertThat(links("u1")).isZero();
    }

    @Test
    void create_locationDown_returns5xx_andNoLink() throws Exception {
        LOCATION_DOWN.set(true);

        mockMvc.perform(post(BASE).with(customer("u1"))
                .contentType(MediaType.APPLICATION_JSON).content(address("Home")))
            .andExpect(status().is5xxServerError())
            .andExpect(jsonPath("$.detail").value("Location service is unavailable, please try again later"));
        assertThat(links("u1")).isZero();
    }

    @Test
    void create_linkSaveFails_deletesTheLocationAddress() throws Exception {
        long nextId = NEXT_ID.get();
        jdbc.update("insert into user_address (user_id, address_id, is_active) values ('u1', ?, false)", nextId);

        mockMvc.perform(post(BASE).with(customer("u1"))
                .contentType(MediaType.APPLICATION_JSON).content(address("Home")))
            .andExpect(status().is4xxClientError());

        assertThat(ADDRESSES).doesNotContainKey(nextId);
        assertThat(links("u1")).isEqualTo(1);
    }

    @Test
    void delete_locationDown_returns5xx_andKeepsTheLink() throws Exception {
        long home = create("u1", "Home");
        LOCATION_DOWN.set(true);

        mockMvc.perform(delete(BASE + "/" + home).with(customer("u1"))).andExpect(status().is5xxServerError());

        assertThat(links("u1")).isEqualTo(1);
    }

    @Test
    void list_locationDown_returns5xx() throws Exception {
        create("u1", "Home");
        LOCATION_DOWN.set(true);

        mockMvc.perform(get(BASE).with(customer("u1"))).andExpect(status().is5xxServerError());
    }
}
