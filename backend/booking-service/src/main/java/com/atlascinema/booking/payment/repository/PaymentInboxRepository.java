
package com.atlascinema.booking.payment.repository;

import com.atlascinema.booking.payment.event.PaymentResultEvent;
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
 * Stores incoming payment events using PostgreSQL constraints
 * Duplicate event ids cannot create additional inbox records
 */
@Repository
@RequiredArgsConstructor
public class PaymentInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean insertIfAbsent(
            PaymentResultEvent paymentResultEvent,
            String eventPayloadJson,
            Instant receivedAt
    ) {
        int insertedRows = jdbcTemplate.update("""
                        INSERT INTO payment_inbox_events (
                            event_id,
                            payment_id,
                            booking_id,
                            event_type,
                            payload,
                            received_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT (event_id) DO NOTHING
                        """,
                paymentResultEvent.eventId(),
                paymentResultEvent.paymentId(),
                paymentResultEvent.bookingId(),
                paymentResultEvent.eventType().name(),
                eventPayloadJson,
                OffsetDateTime.ofInstant(receivedAt, ZoneOffset.UTC));

        return insertedRows == 1;
    }

    public Optional<String> findPayloadByEventId(UUID eventId) {
        List<String> storedPayloads = jdbcTemplate.query("""
                        SELECT payload
                        FROM payment_inbox_events
                        WHERE event_id = ?
                        """,
                (resultSet, rowNumber) ->
                        resultSet.getString("payload"),
                eventId);

        return storedPayloads.stream().findFirst();
    }

    // find events waiting for business processing
    public List<UUID> findUnprocessedEventIds(int limit) {
        return jdbcTemplate.query("""
                    SELECT event_id
                    FROM payment_inbox_events
                    WHERE processed_at IS NULL
                    ORDER BY received_at, event_id
                    LIMIT ?
                    """,
                (resultSet, rowNumber) ->
                        resultSet.getObject("event_id", UUID.class),
                limit);
    }

    // lock one inbox event so concurrent processors cannot handle it twice
    public Optional<LockedPaymentInboxEvent> findByIdForUpdate(UUID eventId) {
        List<LockedPaymentInboxEvent> events = jdbcTemplate.query("""
                    SELECT event_id, payload, processed_at
                    FROM payment_inbox_events
                    WHERE event_id = ?
                    FOR UPDATE
                    """,
                (resultSet, rowNumber) ->
                        new LockedPaymentInboxEvent(
                                resultSet.getObject("event_id", UUID.class),
                                resultSet.getString("payload"),
                                resultSet.getObject("processed_at") != null
                        ), eventId);

        return events.stream().findFirst();
    }

    // Complete inbox processing within the same business transaction
    public void markAsProcessed(UUID eventId, Instant processedAt) {
        int updatedRows = jdbcTemplate.update("""
                    UPDATE payment_inbox_events
                    SET processed_at = ?
                    WHERE event_id = ?
                      AND processed_at IS NULL
                    """,
                OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC),
                eventId);

        if (updatedRows != 1) {
            throw new IllegalStateException("Payment inbox event %s could not be marked as processed"
                            .formatted(eventId));
        }
    }

    public record LockedPaymentInboxEvent(
            UUID eventId,
            String payload,
            boolean processed
    ) {
    }
}
