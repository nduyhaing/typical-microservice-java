package com.shopapp.order.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Details of the logged-in customer taken from the access token (decisions.md 2c-4). */
final class CurrentUser {

    private CurrentUser() {
    }

    /** The {@code email} claim of the customer's token, or null if the token has none. */
    static String email() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token) {
            return token.getToken().getClaimAsString("email");
        }
        return null;
    }
}
