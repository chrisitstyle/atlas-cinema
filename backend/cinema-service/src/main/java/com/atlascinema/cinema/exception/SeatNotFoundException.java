package com.atlascinema.cinema.exception;

import java.util.UUID;

public class SeatNotFoundException extends RuntimeException {

    public SeatNotFoundException(UUID seatId, UUID hallId, UUID cinemaId) {
        super("Seat with id " + seatId
                        + " was not found in hall " + hallId
                        + " in cinema " + cinemaId);
    }
}
