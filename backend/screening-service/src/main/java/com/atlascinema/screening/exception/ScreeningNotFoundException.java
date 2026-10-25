package com.atlascinema.screening.exception;

import java.util.UUID;

public class ScreeningNotFoundException extends RuntimeException {

    public ScreeningNotFoundException(UUID screeningId) {
        super("Screening with id " + screeningId + " was not found");
    }
}
