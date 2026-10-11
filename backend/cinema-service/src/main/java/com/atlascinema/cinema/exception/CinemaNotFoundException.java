package com.atlascinema.cinema.exception;

import java.util.UUID;

public class CinemaNotFoundException extends RuntimeException {

    public CinemaNotFoundException(UUID id) {
        super("Cinema with id " + id + " was not found");
    }
}
