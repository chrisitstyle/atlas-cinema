package com.atlascinema.notification.exception;

/*
 * Signals that notification-service received a booking event type
 * that it does not know how to process
 */
public class UnsupportedBookingEventTypeException extends RuntimeException {

    public UnsupportedBookingEventTypeException(
            String unsupportedEventType
    ) {
        super("Unsupported booking event type: " + unsupportedEventType);
    }
}
