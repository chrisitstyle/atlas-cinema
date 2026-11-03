package com.atlascinema.booking.client.dto;

import java.util.UUID;

/**
 * Identifies the booking for which payment-service should initiate a payment.
 */
public record InitiatePaymentRequest(
        UUID bookingId
) {
}