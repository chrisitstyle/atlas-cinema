package com.atlascinema.movie.exception;

import java.util.UUID;

public class MovieAlreadyDiscontinuedException extends RuntimeException {
    public MovieAlreadyDiscontinuedException(UUID id) {
        super("Movie with id: " + id + " is already discontinued");
    }
}
