package com.atlascinema.notification.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class BaseTestEnvironment {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "postgres:17-alpine");

    static {
        POSTGRES.start();
    }
}
