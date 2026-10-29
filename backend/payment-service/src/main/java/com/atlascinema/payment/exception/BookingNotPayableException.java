package com.atlascinema.payment.exception;

import java.util.UUID;

/*
 * Indicates that a booking does not satisfy payment initiation rules
 */
public class BookingNotPayableException extends RuntimeException {

    public BookingNotPayableException(UUID bookingId, String reason) {
        super("Booking %s is not payable: %s".formatted(bookingId, reason));
    }
}
