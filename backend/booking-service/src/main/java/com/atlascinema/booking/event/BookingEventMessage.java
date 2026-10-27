package com.atlascinema.booking.event;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record BookingEventMessage(
        UUID eventId,
        String eventType,
        UUID bookingId,
        Instant occurredAt,
        JsonNode payload
) {
}
