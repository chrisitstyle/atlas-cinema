package com.atlascinema.notification.event;

import java.time.Instant;
import java.util.UUID;

public record BookingCancelledEvent(
        UUID eventId,
        UUID bookingId,
        UUID customerId,
        String contactEmail,
        UUID screeningId,
        Instant occurredAt
) {
}
