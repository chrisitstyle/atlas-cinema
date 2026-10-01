package com.atlascinema.movie.exception;

import java.util.UUID;

public class MovieAlreadyActiveException extends RuntimeException {
    public MovieAlreadyActiveException(UUID id) {
        super("Movie with id " + id + " is already active");
    }
}
