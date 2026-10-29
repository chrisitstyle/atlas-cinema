package com.atlascinema.payment.domain;

/*
 * Represents the lifecycle of a payment
 * Payment status is independent from the booking status
 */
public enum PaymentStatus {

    // payment has started but its final result is not yet known
    PENDING,

    // payment processing has started but the provider result is not yet confirmed
    PROCESSING,

    // payment was successfully completed
    SUCCEEDED,

    // payment was definitively rejected or failed
    FAILED,

    //  successful payment needs to be refunded
    REFUND_PENDING,

    // the refund has been successfully completed
    REFUNDED
}
