package com.shopapp.storefrontbff.viewmodel;

public record AuthenticationInfoVm(boolean isAuthenticated, AuthenticatedUserVm authenticatedUser) {
}
