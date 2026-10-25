package com.atlascinema.booking.exception;

import java.util.UUID;

public class BookingCancellationNotAllowedException extends RuntimeException {

    public BookingCancellationNotAllowedException(UUID bookingId) {
        super("Booking with id "
                        + bookingId
                        + " cannot be cancelled");
    }
}
