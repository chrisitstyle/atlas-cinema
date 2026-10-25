package com.atlascinema.booking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

// maps booking configuration from application properties
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(Duration holdDuration) { }
