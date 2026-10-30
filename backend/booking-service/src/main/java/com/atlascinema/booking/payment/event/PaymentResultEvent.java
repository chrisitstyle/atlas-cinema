package com.atlascinema.booking.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
 * Represents the payment result event received from Kafka
 * Matches the JSON contract published by payment-service
 */
public record PaymentResultEvent(
        UUID eventId,
        PaymentResultEventType eventType,
        UUID paymentId,
        UUID bookingId,
        BigDecimal amount,
        String currencyCode,
        String providerTransactionId,
        Instant occurredAt
) {
}
