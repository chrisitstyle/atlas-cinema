package com.atlascinema.payment.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/*
 * Provides a real PostgreSQL database for payment integration tests
 * Spring Boot uses the container connection instead of the local database
 */
public abstract class BaseTestEnvironment {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
