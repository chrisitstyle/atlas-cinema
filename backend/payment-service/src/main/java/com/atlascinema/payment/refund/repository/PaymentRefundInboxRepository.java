
package com.atlascinema.payment.refund.repository;

import com.atlascinema.payment.refund.event.BookingEventEnvelope;
import com.atlascinema.payment.refund.event.PaymentRefundRequestedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/*
 * Stores refund requests received from Kafka in PostgreSQL
 * Duplicate event ids do not create additional inbox records
 */
@Repository
@RequiredArgsConstructor
public class PaymentRefundInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean insertIfAbsent(
            BookingEventEnvelope event,
            PaymentRefundRequestedPayload refundRequest,
            String eventPayloadJson,
            Instant receivedAt
    ) {
        int insertedRows = jdbcTemplate.update("""
                        INSERT INTO payment_refund_inbox_events (
                            event_id,
                            payment_id,
                            booking_id,
                            provider_transaction_id,
                            reason,
                            payload,
                            occurred_at,
                            received_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (event_id) DO NOTHING
                        """,
                event.eventId(),
                refundRequest.paymentId(),
                event.bookingId(),
                refundRequest.providerTransactionId(),
                refundRequest.reason(),
                eventPayloadJson,
                OffsetDateTime.ofInstant(
                        event.occurredAt(),
                        ZoneOffset.UTC
                ),
                OffsetDateTime.ofInstant(
                        receivedAt,
                        ZoneOffset.UTC)
        );

        return insertedRows == 1;
    }

    public Optional<String> findPayloadByEventId(UUID eventId) {
        List<String> storedPayloads = jdbcTemplate.query("""
                        SELECT payload
                        FROM payment_refund_inbox_events
                        WHERE event_id = ?
                        """,
                (resultSet, rowNumber) ->
                        resultSet.getString("payload"),
                eventId);

        return storedPayloads.stream().findFirst();
    }

    // Find refund requests whose previous attempt is old enough to retry
    public List<UUID> findEligibleEventIds(
            Instant retryEligibleBefore,
            int limit
    ) {
        return jdbcTemplate.query("""
                    SELECT event_id
                    FROM payment_refund_inbox_events
                    WHERE processed_at IS NULL
                      AND (
                          last_attempt_at IS NULL
                          OR last_attempt_at <= ?
                      )
                    ORDER BY
                        COALESCE(last_attempt_at, received_at),
                        event_id
                    LIMIT ?
                    """,
                (resultSet, rowNumber) ->
                        resultSet.getObject("event_id", UUID.class),
                OffsetDateTime.ofInstant(retryEligibleBefore, ZoneOffset.UTC),
                limit
        );
    }

    // Atomically claim a refund request before starting its processing
    public boolean claimForProcessing(
            UUID eventId,
            Instant attemptedAt,
            Instant retryEligibleBefore
    ) {
        int updatedRows = jdbcTemplate.update("""
                    UPDATE payment_refund_inbox_events
                    SET last_attempt_at = ?
                    WHERE event_id = ?
                      AND processed_at IS NULL
                      AND (
                          last_attempt_at IS NULL
                          OR last_attempt_at <= ?
                      )
                    """,
                OffsetDateTime.ofInstant(attemptedAt, ZoneOffset.UTC),
                eventId,
                OffsetDateTime.ofInstant(retryEligibleBefore, ZoneOffset.UTC)
        );

        return updatedRows == 1;
    }

    // lock an inbox event for processing within the caller's transaction
    public Optional<LockedRefundInboxEvent> findByIdForUpdate(UUID eventId) {
        List<LockedRefundInboxEvent> events = jdbcTemplate.query("""
                    SELECT event_id, payload, processed_at
                    FROM payment_refund_inbox_events
                    WHERE event_id = ?
                    FOR UPDATE
                    """,
                (resultSet, rowNumber) -> new LockedRefundInboxEvent(
                        resultSet.getObject("event_id", UUID.class),
                        resultSet.getString("payload"),
                        resultSet.getObject("processed_at") != null
                ),
                eventId
        );

        return events.stream().findFirst();
    }

    // complete inbox processing within the payment transaction
    public void markAsProcessed(UUID eventId, Instant processedAt) {
        int updatedRows = jdbcTemplate.update("""
                    UPDATE payment_refund_inbox_events
                    SET processed_at = ?
                    WHERE event_id = ?
                      AND processed_at IS NULL
                    """,
                OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC),
                eventId);

        if (updatedRows != 1) {
            throw new IllegalStateException(
                    "Refund inbox event %s could not be marked as processed"
                            .formatted(eventId));
        }
    }

    public record LockedRefundInboxEvent(
            UUID eventId,
            String payload,
            boolean processed) {
    }
}
