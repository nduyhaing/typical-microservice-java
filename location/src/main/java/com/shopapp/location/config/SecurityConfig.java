package com.shopapp.location.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    /**
     * Storefront lookups (countries, states, districts) are public reads (YAS); everything under
     * {@code /backoffice} needs realm role ADMIN (YAS). Saved addresses need a signed-in user and are only visible
     * to their owner or an admin (YAS: {@code /storefront/**} all public, decisions.md 3c).
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/prometheus", "/actuator/health/**", "/swagger-ui", "/swagger-ui/**",
                    "/error", "/v3/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/storefront/countries", "/storefront/state-or-provinces/**",
                    "/storefront/district/**").permitAll()
                .requestMatchers("/storefront/addresses", "/storefront/addresses/**").authenticated()
                .requestMatchers("/backoffice/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .build();
    }

    /**
     * Maps Keycloak realm roles (claim realm_access.roles) to ROLE_* authorities.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverterForKeycloak() {
        Converter<Jwt, Collection<GrantedAuthority>> rolesConverter = jwt -> {
            Map<String, Collection<String>> realmAccess = jwt.getClaim("realm_access");
            Collection<String> roles = realmAccess == null ? List.of() : realmAccess.getOrDefault("roles", List.of());
            return roles.stream().<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
        };
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }
}
