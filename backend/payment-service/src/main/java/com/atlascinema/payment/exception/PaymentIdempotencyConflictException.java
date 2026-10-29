package com.atlascinema.payment.exception;

/*
 * Indicates that an idempotency key was reused for different payment details
 */
public class PaymentIdempotencyConflictException extends RuntimeException {

    public PaymentIdempotencyConflictException() {
        super("Idempotency key is already assigned to different payment details");
    }
}
