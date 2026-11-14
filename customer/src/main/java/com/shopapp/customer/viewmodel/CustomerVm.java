package com.shopapp.customer.viewmodel;

import org.keycloak.representations.idm.UserRepresentation;

public record CustomerVm(String id, String username, String email, String firstName, String lastName) {

    public static CustomerVm fromUserRepresentation(UserRepresentation user) {
        return new CustomerVm(user.getId(), user.getUsername(), user.getEmail(), user.getFirstName(),
            user.getLastName());
    }
}
