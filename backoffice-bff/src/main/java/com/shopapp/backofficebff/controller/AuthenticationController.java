package com.shopapp.backofficebff.controller;

import com.shopapp.backofficebff.viewmodel.AuthenticatedUserVm;
import com.shopapp.backofficebff.viewmodel.AuthenticationInfoVm;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the back office who is logged in (YAS {@code /authentication/user}, same shape as storefront-bff's
 * {@code /authentication}). Only admins get here; everyone else is redirected to login or refused by security.
 * No tokens are returned.
 */
@RestController
public class AuthenticationController {

    @GetMapping("/authentication")
    public ResponseEntity<AuthenticationInfoVm> user(@AuthenticationPrincipal OAuth2User principal) {
        String username = principal.getAttribute("preferred_username");
        return ResponseEntity.ok(new AuthenticationInfoVm(true, new AuthenticatedUserVm(username)));
    }
}
