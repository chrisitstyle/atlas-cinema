package com.atlascinema.screening.exception;

import java.util.UUID;

public class ScreeningAlreadyCancelledException
        extends RuntimeException {

    public ScreeningAlreadyCancelledException(UUID screeningId) {
        super("Screening with id "
                        + screeningId
                        + " is already cancelled");
    }
}
