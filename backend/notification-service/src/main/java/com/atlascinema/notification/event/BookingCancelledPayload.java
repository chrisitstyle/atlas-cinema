package com.atlascinema.notification.event;

import java.util.UUID;

/*
 * Represents event-specific data carried by BookingCancelled messages
 * It is extracted from BookingEventMessage.payload after the event type is recognized
 */
public record BookingCancelledPayload(

        // customer can be null when the booking belongs to a guest
        UUID customerId,

        // used by notification-service to contact the booking owner
        String contactEmail,

        // identifies the screening related to the cancelled booking
        UUID screeningId
) {
}
