package com.atlascinema.notification.event;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/*
 * Represents the common Kafka message structure used for booking events
 * Event-specific data stays inside payload and is interpreted after eventType is known
 */
public record BookingEventMessage(

        // unique id used for idempotent event processing
        UUID eventId,

        // kept as String so unknown future event types can still be deserialized
        String eventType,

        // used to identify which booking this event belongs to
        UUID bookingId,

        // time when the event happened in booking-service
        Instant occurredAt,

        // event-specific data interpreted only after eventType is known
        JsonNode payload
) {
}
