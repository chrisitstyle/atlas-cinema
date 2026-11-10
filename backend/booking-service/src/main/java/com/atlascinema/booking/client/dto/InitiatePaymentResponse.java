package com.atlascinema.booking.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents payment initiation details returned by payment-service.
 */
public record InitiatePaymentResponse(
        UUID paymentId,
        UUID bookingId,
        BigDecimal amount,
        String currencyCode,
        String status,
        Instant createdAt
) {
}
