package com.atlascinema.payment.exception;

import java.util.UUID;

/*
 * Indicates that a requested payment does not exist
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment %s was not found".formatted(paymentId));
    }
}
