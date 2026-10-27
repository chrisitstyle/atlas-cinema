package com.atlascinema.notification.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class BaseTestEnvironment {

    @ServiceConnection
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            "postgres:17-alpine");

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    static {
        POSTGRES.start();
        KAFKA.start();
    }
}
