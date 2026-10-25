package com.atlascinema.booking.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ScreeningNotFoundException.class)
    public ProblemDetail handleScreeningNotFound(
            ScreeningNotFoundException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setTitle("Screening not found");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(ScreeningUnavailableException.class)
    public ProblemDetail handleScreeningUnavailable(
            ScreeningUnavailableException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Screening unavailable");
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

    @ExceptionHandler(SeatAlreadyReservedException.class)
    public ProblemDetail handleSeatAlreadyReserved(
            SeatAlreadyReservedException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Seat already reserved");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(SeatAlreadyAddedException.class)
    public ProblemDetail handleSeatAlreadyAdded(
            SeatAlreadyAddedException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Seat already added");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(BookingNotFoundException.class)
    public ProblemDetail handleBookingNotFound(BookingNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);

        problem.setTitle("Booking not found");
        problem.setDetail(exception.getMessage());

        return problem;
    }

    @ExceptionHandler(BookingCancellationNotAllowedException.class)
    public ProblemDetail handleBookingCancellationNotAllowed(
            BookingCancellationNotAllowedException exception
    ) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);

        problem.setTitle("Booking cancellation not allowed");

        problem.setDetail(exception.getMessage());

        return problem;
    }
}
