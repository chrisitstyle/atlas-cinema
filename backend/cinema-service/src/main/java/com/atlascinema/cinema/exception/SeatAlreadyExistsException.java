package com.atlascinema.cinema.exception;

import java.util.UUID;

public class SeatAlreadyExistsException extends RuntimeException {

    public SeatAlreadyExistsException(UUID hallId, String row, Integer number) {
        super("Seat " + row + number + " already exists in hall " + hallId);
    }
}
