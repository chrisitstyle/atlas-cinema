package com.atlascinema.booking.event;

public enum BookingEventType {

    BOOKING_CANCELLED("BookingCancelled");

    private final String eventName;

    BookingEventType(String eventName) {this.eventName = eventName;}

    public String eventName() {return eventName;}
}
