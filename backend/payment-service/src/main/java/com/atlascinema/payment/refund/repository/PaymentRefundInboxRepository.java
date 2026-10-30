
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
}
