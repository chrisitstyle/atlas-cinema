package com.atlascinema.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * maps Kafka settings used by booking-service from application properties
 */
@ConfigurationProperties(prefix = "booking.kafka")
public record BookingKafkaProperties(String topic) { }
