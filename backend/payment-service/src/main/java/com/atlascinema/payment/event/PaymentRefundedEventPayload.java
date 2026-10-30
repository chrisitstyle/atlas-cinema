package com.atlascinema.payment.event;

import com.atlascinema.payment.domain.PaymentOutboxEventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
 * Describes a completed refund delivered to other microservices
 */
public record PaymentRefundedEventPayload(
        UUID eventId,
        PaymentOutboxEventType eventType,
        UUID paymentId,
        UUID bookingId,
        BigDecimal amount,
        String currencyCode,
        String providerTransactionId,
        String providerRefundTransactionId,
        Instant occurredAt
) {
}