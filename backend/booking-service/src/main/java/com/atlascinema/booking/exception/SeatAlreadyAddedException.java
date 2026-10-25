package com.atlascinema.booking.exception;

import java.util.UUID;

public class SeatAlreadyAddedException extends RuntimeException {

    public SeatAlreadyAddedException(UUID seatId) {
        super("Seat with id "
                        + seatId
                        + " is already added to booking");
    }
}