package com.atlascinema.movie.exception;

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
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                exception.getMessage());

        problem.setTitle("Movie not found");

        return problem;
    }

    @ExceptionHandler(MovieAlreadyDiscontinuedException.class)
    public ProblemDetail handleMovieAlreadyDiscontinued(MovieAlreadyDiscontinuedException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());

        problem.setTitle("Movie already discontinued");
        return problem;
    }


    @ExceptionHandler(MovieAlreadyActiveException.class)
    public ProblemDetail handleMovieAlreadyActive(MovieAlreadyActiveException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());

        problem.setTitle("Movie already active");
        return problem;
    }
}
