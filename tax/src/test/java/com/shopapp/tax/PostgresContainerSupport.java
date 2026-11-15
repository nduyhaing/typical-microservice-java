package com.shopapp.tax;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL 16 container shared by all integration tests of this JVM.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresContainerSupport {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:16.16").withReuse(false);
    }
}
