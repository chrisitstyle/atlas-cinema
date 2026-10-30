package com.atlascinema.payment.domain;

/*
 * Identifies payment events stored for later publication
 */
public enum PaymentOutboxEventType {

    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,
    PAYMENT_REFUNDED
}
