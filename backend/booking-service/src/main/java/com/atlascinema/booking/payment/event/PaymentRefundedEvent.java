package com.atlascinema.booking.payment.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
 * Represents a completed refund published by payment-service
 * Matches the PAYMENT_REFUNDED Kafka JSON contract
 */
public record PaymentRefundedEvent(
        UUID eventId,
        String eventType,
        UUID paymentId,
        UUID bookingId,
        BigDecimal amount,
        String currencyCode,
        String providerTransactionId,
        String providerRefundTransactionId,
        Instant occurredAt
) {
}