package com.atlascinema.screening.exception;

import java.time.LocalDateTime;
import java.util.UUID;

public class ScreeningConflictException extends RuntimeException {

    public ScreeningConflictException(
            UUID hallId,
            LocalDateTime startsAt,
            LocalDateTime endsAt) {
        super(
                "Screening conflicts with another screening in hall "
                        + hallId
                        + " between "
                        + startsAt
                        + " and "
                        + endsAt);
    }
}
