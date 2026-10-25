package com.atlascinema.screening.exception;

import java.util.UUID;

public class MovieUnavailableException extends RuntimeException {

    public MovieUnavailableException(UUID movieId) {
        super("Movie with id " + movieId + " is discontinued");
    }
}
