package com.atlascinema.cinema.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class BaseTestEnvironment {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
                    .withDatabaseName("cinema_test_db");

    static {
        POSTGRES.start();
    }
}
