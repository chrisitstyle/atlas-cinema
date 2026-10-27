package com.atlascinema.booking.event;

import java.util.UUID;

public record BookingCancelledPayload(
        UUID customerId,
        String contactEmail,
        UUID screeningId
) {
}
