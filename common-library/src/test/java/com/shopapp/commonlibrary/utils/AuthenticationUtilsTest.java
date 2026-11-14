package com.shopapp.commonlibrary.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shopapp.commonlibrary.exception.AccessDeniedException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AuthenticationUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateWithJwt() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u-1").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void extractUserId_returnsSubject() {
        authenticateWithJwt();
        assertThat(AuthenticationUtils.extractUserId()).isEqualTo("u-1");
    }

    @Test
    void extractJwt_returnsTokenValue() {
        authenticateWithJwt();
        assertThat(AuthenticationUtils.extractJwt()).isEqualTo("t");
    }

    @Test
    void extractUserId_anonymous_throwsAccessDenied() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        assertThatThrownBy(AuthenticationUtils::extractUserId).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void extractUserId_noAuthentication_throwsAccessDenied() {
        assertThatThrownBy(AuthenticationUtils::extractUserId).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void extractJwt_nonJwtAuthentication_throwsAccessDenied() {
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated("bob", "pw", List.of()));
        assertThatThrownBy(AuthenticationUtils::extractJwt).isInstanceOf(AccessDeniedException.class);
    }
}
