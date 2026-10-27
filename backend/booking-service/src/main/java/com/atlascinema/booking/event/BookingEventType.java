package com.atlascinema.booking.event;

/**
 * Defines booking event types currently published by booking-service
 */
public enum BookingEventType {

    BOOKING_CANCELLED("BookingCancelled"),
    BOOKING_EXPIRED("BookingExpired");

    // exact value used in the Kafka JSON contract
    private final String eventName;

    BookingEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }
}
