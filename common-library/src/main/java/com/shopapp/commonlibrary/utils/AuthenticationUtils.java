package com.shopapp.commonlibrary.utils;

import com.shopapp.commonlibrary.constants.ApiConstant;
import com.shopapp.commonlibrary.exception.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthenticationUtils {

    private AuthenticationUtils() {
    }

    /**
     * Returns the {@code sub} claim of the current JWT.
     *
     * @throws AccessDeniedException when the request is not authenticated with a JWT
     */
    public static String extractUserId() {
        return currentJwt().getSubject();
    }

    /**
     * Returns the raw value of the current JWT, for relaying to downstream services.
     *
     * @throws AccessDeniedException when the request is not authenticated with a JWT
     */
    public static String extractJwt() {
        return currentJwt().getTokenValue();
    }

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private static Jwt currentJwt() {
        if (getAuthentication() instanceof JwtAuthenticationToken jwtAuthentication) {
            return jwtAuthentication.getToken();
        }
        throw new AccessDeniedException(ApiConstant.ACCESS_DENIED);
    }
}
