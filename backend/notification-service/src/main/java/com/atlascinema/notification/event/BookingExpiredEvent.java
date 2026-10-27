package com.atlascinema.notification.event;

import java.time.Instant;
import java.util.UUID;

/*
 * Represents a typed BookingExpired event used internally by notification-service
 * It is created after the generic Kafka message has been routed and its payload decoded
 */
public record BookingExpiredEvent(

        // unique id used for idempotent event processing
        UUID eventId,

        // identifies the booking that expired
        UUID bookingId,

        // customer can be null when the booking belongs to a guest
        UUID customerId,

        // used by notification-service to contact the booking owner
        String contactEmail,

        // identifies the screening related to the expired booking
        UUID screeningId,

        // time when booking-service marked the booking as expired
        Instant occurredAt
) {
}
