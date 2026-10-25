package com.atlascinema.booking.exception;

import java.util.UUID;

public class BookingNotFoundException extends RuntimeException {

    public BookingNotFoundException(UUID bookingId) {
        super("Booking with id " + bookingId + " was not found");
    }
}
