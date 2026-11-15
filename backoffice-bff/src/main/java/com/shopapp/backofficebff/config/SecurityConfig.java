package com.shopapp.backofficebff.config;

import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.server.logout.OidcClientInitiatedServerLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint;
import org.springframework.security.web.server.DelegatingServerAuthenticationEntryPoint.DelegateEntry;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authentication.logout.ServerLogoutSuccessHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

/**
 * Ported from YAS backoffice-bff: every path needs the Keycloak realm role ADMIN except health; login is Keycloak
 * (OIDC), tokens stay in the server session. Anonymous users are sent to the login (anonymous {@code /api/**}
 * calls get 401 JSON), logged-in non-admins get 403.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /** Product's subtract/restore-quantity: never through a BFF, also not for an admin (3c review I3, 3c-63). */
    private static final String[] STOCK_WRITES = {
        "/api/product/backoffice/products/subtract-quantity/**",
        "/api/product/backoffice/products/restore-quantity/**"};

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String LOGIN_PATH = "/oauth2/authorization/keycloak";

    private final ReactiveClientRegistrationRepository clientRegistrationRepository;

    public SecurityConfig(ReactiveClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .authorizeExchange(auth -> auth
                // product's order-only stock endpoints are for the order service inside the network (3c-63)
                .pathMatchers(STOCK_WRITES).denyAll()
                .pathMatchers("/actuator/health", "/actuator/health/**").permitAll()
                .anyExchange().hasRole("ADMIN"))
            .oauth2Login(Customizer.withDefaults())
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint()))
            .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
            .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
            // as YAS: same-site session cookie, no cross-site forms (decisions.md 2b-7)
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .logout(logout -> logout.logoutSuccessHandler(oidcLogoutSuccessHandler()))
            .build();
    }

    /**
     * Anonymous (or expired-session) {@code /api/**} calls get 401 JSON, which a browser fetch can read; pages
     * still redirect to the Keycloak login (decisions.md 3a-2).
     */
    private static ServerAuthenticationEntryPoint authenticationEntryPoint() {
        ServerAuthenticationEntryPoint api = (exchange, exception) ->
            ApiAuthorizationFailureFilter.writeUnauthorized(exchange.getResponse());
        DelegatingServerAuthenticationEntryPoint entryPoint = new DelegatingServerAuthenticationEntryPoint(
            new DelegateEntry(ServerWebExchangeMatchers.pathMatchers("/api/**"), api));
        entryPoint.setDefaultEntryPoint(new RedirectServerAuthenticationEntryPoint(LOGIN_PATH));
        return entryPoint;
    }

    /** Ends the Keycloak session too, then returns to the back office home page (which asks to log in again). */
    private ServerLogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedServerLogoutSuccessHandler handler =
            new OidcClientInitiatedServerLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        // already logged out (no OIDC session): go home instead of Spring's default /login?logout
        handler.setLogoutSuccessUrl(URI.create("/"));
        return handler;
    }

    /** Keycloak realm roles (claim realm_access.roles) become ROLE_* authorities. */
    @Bean
    public GrantedAuthoritiesMapper userAuthoritiesMapperForKeycloak() {
        return authorities -> {
            Set<GrantedAuthority> mapped = new HashSet<>(authorities);
            for (GrantedAuthority authority : authorities) {
                Map<String, Object> claims = switch (authority) {
                    case OidcUserAuthority oidc -> oidc.getUserInfo() != null
                        ? oidc.getUserInfo().getClaims() : oidc.getIdToken().getClaims();
                    case OAuth2UserAuthority oauth2 -> oauth2.getAttributes();
                    default -> Map.of();
                };
                mapped.addAll(rolesFrom(claims));
            }
            return mapped;
        };
    }

    @SuppressWarnings("unchecked")
    private static Collection<GrantedAuthority> rolesFrom(Map<String, Object> claims) {
        if (!(claims.get(REALM_ACCESS_CLAIM) instanceof Map<?, ?> realmAccess)) {
            return List.of();
        }
        Object roles = ((Map<String, Object>) realmAccess).get(ROLES_CLAIM);
        if (!(roles instanceof Collection<?> roleNames)) {
            return List.of();
        }
        return roleNames.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    }
}
