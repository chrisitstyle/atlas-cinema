package com.atlascinema.booking.exception;

import java.util.UUID;

public class SeatNotFoundException
        extends RuntimeException {

    public SeatNotFoundException(UUID seatId) {
        super("Seat with id "
                        + seatId
                        + " was not found in screening hall");
    }
}
