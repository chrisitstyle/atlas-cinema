package com.atlascinema.payment.event;

import com.atlascinema.payment.domain.PaymentOutboxEventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
 * Defines the payment outcome information delivered to other services
 */
public record PaymentResultEventPayload(
        UUID eventId,
        PaymentOutboxEventType eventType,
        UUID paymentId,
        UUID bookingId,
        BigDecimal amount,
        String currencyCode,
        String providerTransactionId,
        Instant occurredAt
) {
}