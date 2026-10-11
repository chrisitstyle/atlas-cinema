package com.atlascinema.cinema.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {


    @ExceptionHandler(CinemaNotFoundException.class)
    public ProblemDetail handleCinemaNotFound(
            CinemaNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setTitle("Cinema not found");
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

    @ExceptionHandler(SeatAlreadyExistsException.class)
    public ProblemDetail handleSeatAlreadyExists(SeatAlreadyExistsException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Seat already exists");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(SeatNotFoundException.class)
    public ProblemDetail handleSeatNotFound(
            SeatNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setTitle("Seat not found");
        problem.setDetail(exception.getMessage());

        return problem;
    }
}
