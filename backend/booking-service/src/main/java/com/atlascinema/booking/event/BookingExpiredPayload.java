package com.atlascinema.booking.event;

import java.util.UUID;

/*
 * Contains event-specific data carried by BookingExpired messages
 */
public record BookingExpiredPayload(

        // customer can be null when the booking belongs to a guest
        UUID customerId,

        // used by downstream services to contact the booking owner
        String contactEmail,

        // identifies the screening related to the expired booking
        UUID screeningId
) {
}