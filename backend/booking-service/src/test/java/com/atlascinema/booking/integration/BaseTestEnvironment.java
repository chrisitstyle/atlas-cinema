package com.atlascinema.booking.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class BaseTestEnvironment {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @ServiceConnection
    protected static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.2");

    @ServiceConnection(name = "redis")
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);
    static {
        POSTGRES.start();
        KAFKA.start();
        REDIS.start();
    }
}
