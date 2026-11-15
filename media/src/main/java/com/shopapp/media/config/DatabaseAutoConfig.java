package com.shopapp.media.config;

import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories("com.shopapp.media.repository")
@EntityScan("com.shopapp.media.model")
public class DatabaseAutoConfig {
}
