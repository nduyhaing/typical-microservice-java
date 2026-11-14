package com.shopapp.storefrontbff.controller;

import com.shopapp.storefrontbff.viewmodel.AuthenticatedUserVm;
import com.shopapp.storefrontbff.viewmodel.AuthenticationInfoVm;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the storefront whether the browser session is logged in (YAS). No tokens are returned.
 */
@RestController
public class AuthenticationController {

    @GetMapping("/authentication")
    public ResponseEntity<AuthenticationInfoVm> user(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.ok(new AuthenticationInfoVm(false, null));
        }
        String username = principal.getAttribute("preferred_username");
        return ResponseEntity.ok(new AuthenticationInfoVm(true, new AuthenticatedUserVm(username)));
    }
}
