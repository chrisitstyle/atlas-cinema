
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
}
