package com.atlascinema.screening.exception;

import java.util.UUID;

public class HallNotFoundException extends RuntimeException {

    public HallNotFoundException(UUID hallId) {
        super("Hall with id " + hallId + " was not found");
    }
}
