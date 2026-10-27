package com.atlascinema.booking.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published after a booking has been cancelled
 *
 * @param eventId unique id of this event instance
 * @param bookingId booking that was cancelled
 * @param customerId customer that owns the booking, null for guest bookings
 * @param contactEmail email that can be used by downstream services
 * @param screeningId screening related to the cancelled booking
 * @param occurredAt moment when the cancellation happened
 */
public record BookingCancelledEvent(
        UUID eventId,
        UUID bookingId,
        UUID customerId,
        String contactEmail,
        UUID screeningId,
        Instant occurredAt
) {
}
