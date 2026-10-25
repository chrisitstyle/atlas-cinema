package com.atlascinema.booking.exception;

import java.util.UUID;

public class ScreeningUnavailableException extends RuntimeException {

    public ScreeningUnavailableException(UUID screeningId) {
        super("Screening with id "
                        + screeningId
                        + " is not available for booking");
    }
}
