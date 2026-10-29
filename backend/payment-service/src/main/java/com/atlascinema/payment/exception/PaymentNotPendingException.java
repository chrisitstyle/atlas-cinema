package com.atlascinema.payment.exception;

import com.atlascinema.payment.domain.PaymentStatus;

import java.util.UUID;

/*
 * Indicates that payment processing cannot start from its current status
 */
public class PaymentNotPendingException extends RuntimeException {

    public PaymentNotPendingException(
            UUID paymentId,
            PaymentStatus currentStatus
    ) {
        super("Payment %s cannot start processing from status %s"
                        .formatted(paymentId, currentStatus));
    }
}
