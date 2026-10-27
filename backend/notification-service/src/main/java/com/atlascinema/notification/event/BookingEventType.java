package com.atlascinema.notification.event;

import java.util.Arrays;
import java.util.Optional;

/*
 * Defines booking event types currently supported by notification-service
 * New values should be added only when the service knows how to process them
 */
public enum BookingEventType {

    // add event types here only when notification-service actually supports them
    BOOKING_CANCELLED("BookingCancelled");

    // exact value used in the Kafka JSON contract
    private final String eventName;

    BookingEventType(String eventName) {
        this.eventName = eventName;
    }

    public String eventName() {
        return eventName;
    }

    public static Optional<BookingEventType> fromEventName(
            String bookingEventName
    ) {
        /*
         * Unknown event types are possible when booking-service evolves
         * before notification-service is updated
         */
        return Arrays.stream(values())
                .filter(bookingEventType ->
                        bookingEventType.eventName.equals(bookingEventName))
                .findFirst();
    }
}
