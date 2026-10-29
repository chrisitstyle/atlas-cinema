
package com.atlascinema.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/*
 * Stores a payment event in the same database transaction as its payment
 * The event remains unpublished until the outbox publisher delivers it
 */
@Entity
@Table(name = "payment_outbox_events",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_payment_outbox_payment_type",
                columnNames = {"payment_id", "event_type"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentOutboxEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32, updatable = false)
    private PaymentOutboxEventType eventType;

    @Column(nullable = false, columnDefinition = "TEXT", updatable = false)
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    public static PaymentOutboxEvent create(
            UUID eventId,
            UUID paymentId,
            PaymentOutboxEventType eventType,
            String payload,
            Instant occurredAt
    ) {
        Objects.requireNonNull(eventId, "Event id is required");
        Objects.requireNonNull(paymentId, "Payment id is required");
        Objects.requireNonNull(eventType, "Event type is required");
        Objects.requireNonNull(occurredAt, "Event occurrence time is required");

        if (payload == null || payload.isBlank()) {
            throw new IllegalArgumentException("Event payload must not be blank");
        }

        PaymentOutboxEvent outboxEvent = new PaymentOutboxEvent();

        outboxEvent.id = eventId;
        outboxEvent.paymentId = paymentId;
        outboxEvent.eventType = eventType;
        outboxEvent.payload = payload;
        outboxEvent.occurredAt = occurredAt;

        return outboxEvent;
    }
}
