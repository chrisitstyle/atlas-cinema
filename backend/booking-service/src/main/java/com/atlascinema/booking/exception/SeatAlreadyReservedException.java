package com.atlascinema.booking.exception;

import java.util.UUID;

public class SeatAlreadyReservedException
        extends RuntimeException {

    public SeatAlreadyReservedException(UUID screeningId) {
        super("One or more selected seats are already reserved "
                        + "for screening " + screeningId);
    }
}
