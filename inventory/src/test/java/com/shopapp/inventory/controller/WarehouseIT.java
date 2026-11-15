package com.shopapp.inventory.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.inventory.support.AbstractInventoryIT;
import com.shopapp.inventory.support.ServicesStub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

class WarehouseIT extends AbstractInventoryIT {

    @Test
    void crud_happyPath_keepsTheAddressInLocation() throws Exception {
        mockMvc.perform(post("/backoffice/warehouses").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(warehouseJson("Main", 1)))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", containsString("/backoffice/warehouses/1")))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("Main"));
        assertThat(STUB.requests()).anyMatch(r -> r.equals("POST /location/storefront/addresses | Bearer "
            + ADMIN_TOKEN));
        assertThat(STUB.addresses().keySet())
            .containsExactly(jdbc.queryForObject("select address_id from warehouse where id = 1", Long.class));
        createWarehouse("Annex");

        mockMvc.perform(get("/backoffice/warehouses/1").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Main"))
            .andExpect(jsonPath("$.contactName").value("Store keeper"))
            .andExpect(jsonPath("$.addressLine1").value("1 Le Loi"))
            .andExpect(jsonPath("$.countryId").value(1));
        mockMvc.perform(get("/backoffice/warehouses").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", contains("Annex", "Main")));
        mockMvc.perform(get("/backoffice/warehouses/paging?pageNo=0&pageSize=1").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.warehouseContent", hasSize(1)))
            .andExpect(jsonPath("$.warehouseContent[0].name").value("Main"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.isLast").value(false));

        mockMvc.perform(put("/backoffice/warehouses/1").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(warehouseJson("Main North", 1).replace("1 Le Loi", "9 Nguyen Hue")))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/backoffice/warehouses/1").with(admin()))
            .andExpect(jsonPath("$.name").value("Main North"))
            .andExpect(jsonPath("$.addressLine1").value("9 Nguyen Hue"));

        long addressId = jdbc.queryForObject("select address_id from warehouse where id = 1", Long.class);
        mockMvc.perform(delete("/backoffice/warehouses/1").with(admin())).andExpect(status().isNoContent());
        assertThat(STUB.addresses()).doesNotContainKey(addressId).hasSize(1);
        mockMvc.perform(get("/backoffice/warehouses/1").with(admin())).andExpect(status().isNotFound());
        mockMvc.perform(put("/backoffice/warehouses/1").with(admin()).contentType(MediaType.APPLICATION_JSON)
            .content(warehouseJson("X", 1))).andExpect(status().isNotFound());
        mockMvc.perform(delete("/backoffice/warehouses/1").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void customer_isForbidden_anonymous_isUnauthorized() throws Exception {
        long id = createWarehouse("Main");
        mockMvc.perform(get("/backoffice/warehouses").with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/warehouses/" + id).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(post("/backoffice/warehouses").with(customer())
                .contentType(MediaType.APPLICATION_JSON).content(warehouseJson("Hacked", 1)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/backoffice/warehouses/" + id).with(customer())
                .contentType(MediaType.APPLICATION_JSON).content(warehouseJson("Hacked", 1)))
            .andExpect(status().isForbidden());
        mockMvc.perform(delete("/backoffice/warehouses/" + id).with(customer())).andExpect(status().isForbidden());
        mockMvc.perform(get("/backoffice/warehouses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/backoffice/warehouses/paging")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/backoffice/warehouses").contentType(MediaType.APPLICATION_JSON)
            .content(warehouseJson("Hacked", 1))).andExpect(status().is4xxClientError());
        assertThat(count("warehouse")).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"contactName\":\"a\",\"phone\":\"1\",\"addressLine1\":\"x\",\"countryId\":1}",
        "{\"name\":\"  \",\"contactName\":\"a\",\"phone\":\"1\",\"addressLine1\":\"x\",\"countryId\":1}",
        "{\"name\":\"W\",\"phone\":\"1\",\"addressLine1\":\"x\",\"countryId\":1}",
        "{\"name\":\"W\",\"contactName\":\"a\",\"phone\":\"1\",\"addressLine1\":\"x\"}",
        "{\"name\":\"W\",\"contactName\":\"a\",\"phone\":\"12345678901234567890123456\",\"addressLine1\":\"x\","
            + "\"countryId\":1}"})
    void invalidBody_isBadRequest_andNoAddressIsCreated(String body) throws Exception {
        mockMvc.perform(post("/backoffice/warehouses").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
        assertThat(STUB.addresses()).isEmpty();
        assertThat(count("warehouse")).isZero();
    }

    @Test
    void duplicateName_ignoringCase_isBadRequest() throws Exception {
        long annex = createWarehouse("Main");
        createWarehouse("Annex");
        mockMvc.perform(post("/backoffice/warehouses").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(warehouseJson("main", 1)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("main")));
        assertThat(STUB.addresses()).hasSize(2);
        mockMvc.perform(put("/backoffice/warehouses/2").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(warehouseJson("MAIN", 1)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/backoffice/warehouses/" + annex).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(warehouseJson("Main", 1)))
            .andExpect(status().isNoContent());
    }

    @Test
    void pagingOutOfBounds_isBadRequest() throws Exception {
        mockMvc.perform(get("/backoffice/warehouses/paging?pageNo=-1").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/warehouses/paging?pageSize=0").with(admin()))
            .andExpect(status().isBadRequest());
        mockMvc.perform(get("/backoffice/warehouses/paging?pageSize=101").with(admin()))
            .andExpect(status().isBadRequest());
    }

    @Test
    void locationRefusesTheAddress_isBadRequest_andNoWarehouse() throws Exception {
        mockMvc.perform(post("/backoffice/warehouses").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content(warehouseJson("Main", ServicesStub.BAD_COUNTRY_ID)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("Country 999")));
        assertThat(count("warehouse")).isZero();
    }

    @Test
    void locationDown_isServerError_andNothingChanges() throws Exception {
        long id = createWarehouse("Main");
        STUB.locationDown();

        mockMvc.perform(post("/backoffice/warehouses").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(warehouseJson("Annex", 1)))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.detail").value(containsString("Location service")));
        assertThat(count("warehouse")).isEqualTo(1);

        mockMvc.perform(put("/backoffice/warehouses/" + id).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(warehouseJson("Renamed", 1)))
            .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("select name from warehouse where id = ?", String.class, id))
            .isEqualTo("Main");

        mockMvc.perform(delete("/backoffice/warehouses/" + id).with(admin()))
            .andExpect(status().isInternalServerError());
        assertThat(count("warehouse")).isEqualTo(1);

        mockMvc.perform(get("/backoffice/warehouses/" + id).with(admin()))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void deleteWarehouse_withStock_isBadRequest_withOnlyEmptyStock_isAllowed() throws Exception {
        STUB.product(10, "Phone", "PH-1", true);
        long id = createWarehouse("Main");
        long stockId = addStock(10, id);
        adjust(stockId, 2, "received");

        mockMvc.perform(delete("/backoffice/warehouses/" + id).with(admin()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.detail").value(containsString("stock")));
        assertThat(count("warehouse")).isEqualTo(1);

        adjust(stockId, -2, "moved out");
        mockMvc.perform(delete("/backoffice/warehouses/" + id).with(admin())).andExpect(status().isNoContent());
        assertThat(count("warehouse")).isZero();
        assertThat(count("stock")).isZero();
        assertThat(count("stock_history")).isZero();
    }
}
