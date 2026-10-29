package com.atlascinema.payment.exception;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/*
 * Translates payment initiation failures into HTTP responses
 * Avoids exposing internal Feign and database error details
 */
@RestControllerAdvice(basePackages = "com.atlascinema.payment.controller")
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentIdempotencyConflictException.class)
    public ProblemDetail handleIdempotencyConflict(PaymentIdempotencyConflictException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
    }

    @ExceptionHandler(BookingNotPayableException.class)
    public ProblemDetail handleBookingNotPayable(BookingNotPayableException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                exception.getMessage());
    }

    @ExceptionHandler(FeignException.NotFound.class)
    public ProblemDetail handleBookingNotFound() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Booking was not found");
    }

    @ExceptionHandler(FeignException.class)
    public ProblemDetail handleBookingServiceFailure() {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "Could not retrieve booking details");
    }
}
