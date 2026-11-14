package com.shopapp.cart.config;

import java.util.Optional;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration
@EnableJpaRepositories("com.shopapp.cart.repository")
@EntityScan("com.shopapp.cart.model")
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class DatabaseAutoConfig {

    /**
     * The current user's name for createdBy/lastModifiedBy; empty for anonymous requests
     * (storefront reads), so auditing never fails and leaves the fields null.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }
            return Optional.of(auth.getName());
        };
    }
}
