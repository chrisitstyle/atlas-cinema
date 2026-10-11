package com.atlascinema.cinema.exception;

import java.util.UUID;

public class HallNotFoundException extends RuntimeException {

    public HallNotFoundException(
            UUID hallId,
            UUID cinemaId) {
        super(
                "Hall with id " + hallId
                        + " was not found in cinema " + cinemaId);
    }
}
