package com.atlascinema.payment.refund.event;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/*
 * Represents the common event envelope published by booking-service
 */
public record BookingEventEnvelope(
        UUID eventId,
        String eventType,
        UUID bookingId,
        Instant occurredAt,
        JsonNode payload
) {
}