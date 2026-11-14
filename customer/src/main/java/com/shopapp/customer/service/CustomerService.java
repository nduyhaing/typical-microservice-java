package com.shopapp.customer.service;

import com.shopapp.commonlibrary.exception.AccessDeniedException;
import com.shopapp.customer.config.KeycloakPropsConfig;
import com.shopapp.customer.viewmodel.CustomerVm;
import jakarta.ws.rs.ForbiddenException;
import lombok.RequiredArgsConstructor;
import org.keycloak.admin.client.Keycloak;
import org.springframework.stereotype.Service;

/**
 * Customer data lives in Keycloak (YAS). Slice 2b: the current user's profile only.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final String ERROR_FORMAT = "%s: Client %s doesn't have access right for this resource";

    private final Keycloak keycloak;
    private final KeycloakPropsConfig keycloakPropsConfig;

    public CustomerVm getCustomerProfile(String userId) {
        try {
            return CustomerVm.fromUserRepresentation(
                keycloak.realm(keycloakPropsConfig.realm()).users().get(userId).toRepresentation());
        } catch (ForbiddenException exception) {
            throw new AccessDeniedException(
                String.format(ERROR_FORMAT, exception.getMessage(), keycloakPropsConfig.resource()));
        }
    }
}
