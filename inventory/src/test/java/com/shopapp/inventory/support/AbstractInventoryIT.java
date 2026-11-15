package com.shopapp.inventory.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.shopapp.inventory.PostgresContainerSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Full application, real PostgreSQL, product and location replaced by one {@link ServicesStub} shared by every test
 * class (same properties, so one cached Spring context); tables and stub emptied before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
public abstract class AbstractInventoryIT {

    protected static final String ADMIN_TOKEN = "admin-token";
    protected static final ServicesStub STUB = new ServicesStub();

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void services(DynamicPropertyRegistry registry) {
        registry.add("shopapp.services.product", STUB::productUrl);
        registry.add("shopapp.services.location", STUB::locationUrl);
    }

    @BeforeEach
    void cleanState() {
        jdbc.execute("TRUNCATE stock_history, stock, warehouse RESTART IDENTITY");
        STUB.reset();
    }

    protected static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.tokenValue(ADMIN_TOKEN).subject("admin-user"))
            .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    protected static RequestPostProcessor customer() {
        return jwt().jwt(token -> token.subject("customer-1"))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    protected static String warehouseJson(String name, long countryId) {
        return """
            {"name":"%s","contactName":"Store keeper","phone":"0901234567","addressLine1":"1 Le Loi",
             "addressLine2":null,"city":"Ho Chi Minh","zipCode":"700000","districtId":null,
             "stateOrProvinceId":null,"countryId":%d}
            """.formatted(name, countryId);
    }

    protected long createWarehouse(String name) throws Exception {
        String body = mockMvc.perform(post("/backoffice/warehouses").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(warehouseJson(name, 1)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    /** Registers the product in the warehouse (quantity 0) and returns the stock id. */
    protected long addStock(long productId, long warehouseId) throws Exception {
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId + "}]"))
            .andExpect(status().isNoContent());
        return jdbc.queryForObject("select id from stock where product_id = ? and warehouse_id = ?", Long.class,
            productId, warehouseId);
    }

    protected void adjust(long stockId, long quantity, String note) throws Exception {
        mockMvc.perform(put("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(adjustJson(stockId, quantity, note)))
            .andExpect(status().isOk());
    }

    protected static String adjustJson(long stockId, Long quantity, String note) {
        return "{\"stockQuantityList\":[{\"stockId\":" + stockId + ",\"quantity\":" + quantity + ",\"note\":"
            + (note == null ? "null" : "\"" + note + "\"") + "}]}";
    }

    protected long quantityOf(long stockId) {
        return jdbc.queryForObject("select quantity from stock where id = ?", Long.class, stockId);
    }

    protected int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
