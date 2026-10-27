package com.atlascinema.booking.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores an event that must be published to Kafka after the database transaction commits
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    private UUID id;

    // identifies the domain object this event belongs to, for example a booking
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    // logical event name used to decide how the payload should be published
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // stores the complete event as JSON so it can be published later without rebuilding it
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    // records when the event was added to the outbox
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Null means the event has not been successfully published yet
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    private OutboxEvent(
            UUID id,
            UUID aggregateId,
            String eventType,
            String payload,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
        this.publishedAt = null;
    }

    public static OutboxEvent create(
            UUID eventId,
            UUID aggregateId,
            String eventType,
            String payload,
            LocalDateTime createdAt) {
        return new OutboxEvent(
                eventId,
                aggregateId,
                eventType,
                payload,
                createdAt);
    }

    public void markAsPublished(
            LocalDateTime publishedAt
    ) {
        this.publishedAt = publishedAt;
    }
}
