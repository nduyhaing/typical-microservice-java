package com.shopapp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.inventory.support.AbstractInventoryIT;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class StockIT extends AbstractInventoryIT {

    private long main;

    @BeforeEach
    void catalog() throws Exception {
        STUB.product(10, "Phone", "PH-1", true);
        STUB.product(11, "Cable", "CA-1", true);
        STUB.product(12, "Gift card", "GC-1", false);
        main = createWarehouse("Main");
    }

    private static String stockPost(long productId, long warehouseId) {
        return "[{\"productId\":" + productId + ",\"warehouseId\":" + warehouseId + "}]";
    }

    @Test
    void addProducts_thenList_withProductInfo() throws Exception {
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"productId\":10,\"warehouseId\":" + main + "},{\"productId\":11,\"warehouseId\":"
                    + main + "}]"))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/backoffice/stocks").param("warehouseId", String.valueOf(main)).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].productName", contains("Phone", "Cable")))
            .andExpect(jsonPath("$[0].productSku").value("PH-1"))
            .andExpect(jsonPath("$[0].quantity").value(0))
            .andExpect(jsonPath("$[0].reservedQuantity").value(0))
            .andExpect(jsonPath("$[0].warehouseId").value(main));
        mockMvc.perform(get("/backoffice/stocks").param("warehouseId", String.valueOf(main))
                .param("productName", "cab").with(admin()))
            .andExpect(jsonPath("$[*].productName", contains("Cable")));
        mockMvc.perform(get("/backoffice/stocks").param("warehouseId", String.valueOf(main))
                .param("productSku", "ph-").with(admin()))
            .andExpect(jsonPath("$[*].productName", contains("Phone")));

        // the products' stock quantity now follows the warehouses (total 0)
        assertThat(STUB.quantityUpdates()).containsExactly(
            "[{\"productId\":10,\"stockQuantity\":0},{\"productId\":11,\"stockQuantity\":0}]");
        assertThat(STUB.requests()).filteredOn(r -> r.startsWith("PUT /product"))
            .allMatch(r -> r.endsWith("Bearer " + ADMIN_TOKEN));
    }

    @Test
    void addProducts_refusals() throws Exception {
        addStock(10, main);
        STUB.quantityUpdates();
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(stockPost(10, main)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(stockPost(11, 999)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(stockPost(404, main)))
            .andExpect(status().isNotFound());
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(stockPost(12, main)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("stock tracking")));
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"productId\":11,\"warehouseId\":" + main + "},{\"productId\":11,\"warehouseId\":"
                    + main + "}]"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"productId\":null,\"warehouseId\":" + main + "}]"))
            .andExpect(status().isBadRequest());
        assertThat(count("stock")).isEqualTo(1);
    }

    @Test
    void adjust_changesQuantity_writesHistory_andSyncsTheProductTotal() throws Exception {
        long stockId = addStock(10, main);
        long annex = createWarehouse("Annex");
        long annexStock = addStock(10, annex);

        adjust(stockId, 5, "first delivery");
        adjust(stockId, 3, "second delivery");
        assertThat(quantityOf(stockId)).isEqualTo(8);
        assertThat(STUB.quantityUpdates()).last().isEqualTo("[{\"productId\":10,\"stockQuantity\":8}]");

        adjust(annexStock, 2, "annex delivery");
        assertThat(STUB.quantityUpdates()).last().isEqualTo("[{\"productId\":10,\"stockQuantity\":10}]");

        mockMvc.perform(get("/backoffice/stocks/histories").param("productId", "10")
                .param("warehouseId", String.valueOf(main)).with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[*].note", contains("second delivery", "first delivery")))
            .andExpect(jsonPath("$.data[*].adjustedQuantity", contains(3, 5)))
            .andExpect(jsonPath("$.data[0].productName").value("Phone"))
            .andExpect(jsonPath("$.data[0].createdBy").value("admin-user"))
            .andExpect(jsonPath("$.data[0].createdOn").exists());
    }

    @Test
    void adjust_severalStocksInOneCall() throws Exception {
        long phone = addStock(10, main);
        long cable = addStock(11, main);
        mockMvc.perform(put("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"stockQuantityList\":[{\"stockId\":" + phone + ",\"quantity\":4,\"note\":\"a\"},"
                    + "{\"stockId\":" + cable + ",\"quantity\":6,\"note\":\"b\"}]}"))
            .andExpect(status().isOk());
        assertThat(quantityOf(phone)).isEqualTo(4);
        assertThat(quantityOf(cable)).isEqualTo(6);
        assertThat(STUB.quantityUpdates()).last()
            .isEqualTo("[{\"productId\":10,\"stockQuantity\":4},{\"productId\":11,\"stockQuantity\":6}]");
        assertThat(count("stock_history")).isEqualTo(2);
    }

    @Test
    void adjust_toANegativeQuantity_isBadRequest_andNothingChanges() throws Exception {
        long stockId = addStock(10, main);
        adjust(stockId, 2, "in");
        int updates = STUB.quantityUpdates().size();

        mockMvc.perform(put("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(adjustJson(stockId, -3L, "too many out")))
            .andExpect(status().isBadRequest());
        assertThat(quantityOf(stockId)).isEqualTo(2);
        assertThat(count("stock_history")).isEqualTo(1);
        assertThat(STUB.quantityUpdates()).hasSize(updates);
    }

    @Test
    void adjust_invalidInput() throws Exception {
        long stockId = addStock(10, main);
        for (String body : List.of("{\"stockQuantityList\":[]}", "{}", adjustJson(stockId, null, "x"),
            adjustJson(stockId, 1L, "n".repeat(451)),
            "{\"stockQuantityList\":[{\"stockId\":null,\"quantity\":1}]}",
            "{\"stockQuantityList\":[{\"stockId\":" + stockId + ",\"quantity\":1},{\"stockId\":" + stockId
                + ",\"quantity\":1}]}")) {
            mockMvc.perform(put("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(adjustJson(9999, 1L, "x")))
            .andExpect(status().isNotFound());
        assertThat(quantityOf(stockId)).isZero();
        assertThat(count("stock_history")).isZero();
    }

    @Test
    void productDown_onAdjust_isServerError_andNoStockChange() throws Exception {
        long stockId = addStock(10, main);
        adjust(stockId, 4, "in");
        STUB.productDown();

        mockMvc.perform(put("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(adjustJson(stockId, 3L, "more")))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value(containsString("Product service")));
        assertThat(quantityOf(stockId)).isEqualTo(4);
        assertThat(count("stock_history")).isEqualTo(1);
    }

    @Test
    void productDown_onAdd_isServerError_andNoStockRow() throws Exception {
        STUB.productDown();
        mockMvc.perform(post("/backoffice/stocks").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(stockPost(10, main)))
            .andExpect(status().isInternalServerError());
        assertThat(count("stock")).isZero();
    }

    @Test
    void lists_unknownWarehouse_isNotFound() throws Exception {
        mockMvc.perform(get("/backoffice/stocks").param("warehouseId", "999").with(admin()))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/backoffice/stocks/histories").param("productId", "10").param("warehouseId", "999")
            .with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(get("/backoffice/warehouses/999/products").with(admin()))
            .andExpect(status().isNotFound());
    }

    @Test
    void warehouseProducts_marksWhichAreInTheWarehouse() throws Exception {
        addStock(10, main);
        mockMvc.perform(get("/backoffice/warehouses/" + main + "/products").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", contains("Phone", "Cable", "Gift card")))
            .andExpect(jsonPath("$[0].existInWh").value(true))
            .andExpect(jsonPath("$[1].existInWh").value(false));
        mockMvc.perform(get("/backoffice/warehouses/" + main + "/products").param("existStatus", "NO")
                .with(admin()))
            .andExpect(jsonPath("$[*].name", contains("Cable", "Gift card")));
        mockMvc.perform(get("/backoffice/warehouses/" + main + "/products").param("existStatus", "YES")
                .param("productName", "pho").with(admin()))
            .andExpect(jsonPath("$[*].name", contains("Phone")));
    }

    @Test
    void customer_isForbidden_anonymous_isUnauthorized() throws Exception {
        long stockId = addStock(10, main);
        mockMvc.perform(post("/backoffice/stocks").with(customer()).contentType(MediaType.APPLICATION_JSON)
            .content(stockPost(11, main))).andExpect(status().isForbidden());
        mockMvc.perform(put("/backoffice/stocks").with(customer()).contentType(MediaType.APPLICATION_JSON)
            .content(adjustJson(stockId, 5L, "x"))).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/stocks").param("warehouseId", String.valueOf(main)).with(customer()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/stocks/histories").param("productId", "10")
            .param("warehouseId", String.valueOf(main)).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/stocks").param("warehouseId", String.valueOf(main)))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/backoffice/stocks").contentType(MediaType.APPLICATION_JSON)
            .content(adjustJson(stockId, 5L, "x"))).andExpect(status().is4xxClientError());
        assertThat(quantityOf(stockId)).isZero();
        assertThat(count("stock")).isEqualTo(1);
    }
}
