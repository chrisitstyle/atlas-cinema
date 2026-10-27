package com.atlascinema.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Maps Kafka settings used by notification-service from application properties
 */
@ConfigurationProperties(prefix = "notification.kafka")
public record NotificationKafkaProperties(
        String bookingEventsTopic,
        String bookingEventsDltTopic,
        Duration retryInterval,
        long maxRetries
) {
}
