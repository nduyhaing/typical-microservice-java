package com.shopapp.product.config;

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
     * subtract-quantity and restore-quantity are called by the order service with the customer's relayed token, so
     * any signed-in user may call them (they can only take stock or give back their own reservation, 3c-21/3c-22);
     * everything else under /backoffice needs realm role ADMIN.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/prometheus", "/actuator/health/**", "/swagger-ui", "/swagger-ui/**",
                    "/error", "/v3/api-docs/**").permitAll()
                .requestMatchers("/storefront/**").permitAll()
                .requestMatchers(HttpMethod.PUT, "/backoffice/products/subtract-quantity",
                    "/backoffice/products/restore-quantity").authenticated()
                .requestMatchers("/backoffice/**").hasRole("ADMIN")
                // order service only, with its client-credentials token; also refused at the edge (5-5)
                .requestMatchers("/internal/**").hasRole("SERVICE")
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
