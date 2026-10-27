package com.atlascinema.notification.event;

import java.util.UUID;

/*
 * Contains event-specific data carried by BookingExpired messages
 * It is extracted from BookingEventMessage.payload after the event type is recognized
 */
public record BookingExpiredPayload(

        // customer can be null when the booking belongs to a guest
        UUID customerId,

        // used by notification-service to contact the booking owner
        String contactEmail,

        // identifies the screening related to the expired booking
        UUID screeningId
) {
}
