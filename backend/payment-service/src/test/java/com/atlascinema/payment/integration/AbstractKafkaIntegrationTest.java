
package com.atlascinema.payment.integration;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/*
 * Provides a shared Kafka container for integration tests
 * Inherits PostgreSQL and Spring configuration from AbstractIntegrationTest
 */
@TestPropertySource(properties = {
        "spring.kafka.admin.auto-create=true",
        "spring.kafka.admin.fail-fast=true",
        "payment.outbox.publisher.scheduler.enabled=false",
        "payment.reconciliation.scheduler.enabled=false"
})
public abstract class AbstractKafkaIntegrationTest extends AbstractIntegrationTest {

    @ServiceConnection
    protected static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.2"));

    static {
        KAFKA.start();
    }
}
