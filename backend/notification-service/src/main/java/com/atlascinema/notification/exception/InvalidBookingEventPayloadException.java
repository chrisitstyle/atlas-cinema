package com.atlascinema.notification.exception;

import java.util.UUID;

/*
 * Signals that the common booking event message was valid
 * but its event-specific payload could not be interpreted
 */
public class InvalidBookingEventPayloadException extends RuntimeException {

    public InvalidBookingEventPayloadException(
            UUID eventId,
            String eventType,
            Throwable cause) {
        super("Invalid payload for booking event %s with type %s"
                        .formatted(eventId, eventType), cause);
    }
}
