package com.atlascinema.screening.exception;

public class InvalidScreeningFilterException
        extends RuntimeException {

    public InvalidScreeningFilterException() {
        super("Only one screening filter can be used at a time");
    }
}
