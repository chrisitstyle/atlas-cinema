package com.atlascinema.booking.event;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/*
 * Requests deletion of temporary seat holds after booking cancellation commits
 * The immutable seat list is independent of the JPA persistence context
 */
public record BookingSeatHoldsReleaseRequested(
        UUID bookingId,
        UUID screeningId,
        List<UUID> seatIds
) {
    public BookingSeatHoldsReleaseRequested {
        Objects.requireNonNull(bookingId, "Booking id is required");
        Objects.requireNonNull(screeningId, "Screening id is required");
        seatIds = List.copyOf(Objects.requireNonNull(seatIds, "Seat ids are required"));
    }
}