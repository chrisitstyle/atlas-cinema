package com.atlascinema.booking.event;

import java.time.Instant;
import java.util.UUID;

/*
 * Represents the domain event created when a held booking expires
 * It is later transformed into the common Kafka BookingEventMessage
 */
public record BookingExpiredEvent(

        // unique id used for idempotent processing by downstream consumers
        UUID eventId,

        // identifies the booking that expired
        UUID bookingId,

        // customer can be null when the booking belongs to a guest
        UUID customerId,

        // used by downstream services to contact the booking owner
        String contactEmail,

        // identifies the screening related to the expired booking
        UUID screeningId,

        // time when booking-service marked the booking as expired
        Instant occurredAt
) {
}