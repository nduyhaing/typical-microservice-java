package com.shopapp.customer.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shopapp.customer.PostgresContainerSupport;
import jakarta.ws.rs.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresContainerSupport.class)
class CustomerControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private Keycloak keycloak;

    private static RequestPostProcessor customer(String id) {
        return jwt().jwt(token -> token.subject(id)).authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @Test
    void profile_returnsCurrentUser() throws Exception {
        UserRepresentation user = new UserRepresentation();
        user.setId("u-1");
        user.setUsername("customer");
        user.setEmail("customer@shopapp.local");
        user.setFirstName("Customer");
        user.setLastName("Dev");
        when(keycloak.realm("shopapp").users().get("u-1").toRepresentation()).thenReturn(user);

        mockMvc.perform(get("/storefront/customer/profile").with(customer("u-1")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("u-1"))
            .andExpect(jsonPath("$.username").value("customer"))
            .andExpect(jsonPath("$.email").value("customer@shopapp.local"))
            .andExpect(jsonPath("$.firstName").value("Customer"))
            .andExpect(jsonPath("$.lastName").value("Dev"));
    }

    @Test
    void profile_anonymous_returns401() throws Exception {
        mockMvc.perform(get("/storefront/customer/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void profile_withoutCustomerRole_returns403() throws Exception {
        mockMvc.perform(get("/storefront/customer/profile").with(jwt().jwt(token -> token.subject("u-1"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void profile_keycloakForbidden_returns403ErrorVm() throws Exception {
        when(keycloak.realm("shopapp").users().get("u-2").toRepresentation()).thenThrow(new ForbiddenException());

        mockMvc.perform(get("/storefront/customer/profile").with(customer("u-2")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.statusCode").value("403 FORBIDDEN"));
    }
}
