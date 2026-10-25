package com.atlascinema.screening.exception;

import java.util.UUID;

public class MovieNotFoundException extends RuntimeException {

    public MovieNotFoundException(UUID movieId) {
        super("Movie with id " + movieId + " was not found");
    }
}
