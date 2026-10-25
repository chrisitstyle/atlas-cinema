package com.atlascinema.screening.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MovieNotFoundException.class)
    public ProblemDetail handleMovieNotFound(
            MovieNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setTitle("Movie not found");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(HallNotFoundException.class)
    public ProblemDetail handleHallNotFound(
            HallNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setTitle("Hall not found");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(MovieUnavailableException.class)
    public ProblemDetail handleMovieUnavailable(
            MovieUnavailableException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Movie unavailable");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(ScreeningConflictException.class)
    public ProblemDetail handleScreeningConflict(
            ScreeningConflictException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Screening conflict");
        problem.setDetail(exception.getMessage());

        return problem;
    }
}
