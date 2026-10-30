
package com.atlascinema.booking.payment.repository;

import com.atlascinema.booking.payment.event.PaymentRefundedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/*
 * Persists completed refund confirmations in PostgreSQL
 * Unique constraints protect event and payment-level idempotency
 */
@Repository
@RequiredArgsConstructor
public class PaymentRefundConfirmationRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean insertIfAbsent(
            PaymentRefundedEvent event,
            String eventPayloadJson,
            Instant receivedAt
    ) {
        int insertedRows = jdbcTemplate.update("""
                        INSERT INTO payment_refund_confirmations (
                            event_id,
                            payment_id,
                            booking_id,
                            provider_transaction_id,
                            provider_refund_transaction_id,
                            amount,
                            currency_code,
                            payload,
                            received_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """,
                event.eventId(),
                event.paymentId(),
                event.bookingId(),
                event.providerTransactionId(),
                event.providerRefundTransactionId(),
                event.amount(),
                event.currencyCode(),
                eventPayloadJson,
                OffsetDateTime.ofInstant(receivedAt, ZoneOffset.UTC));

        return insertedRows == 1;
    }

    // find an existing confirmation after a uniqueness conflict
    public List<StoredRefundConfirmation> findConflictingConfirmations(
            UUID eventId,
            UUID paymentId
    ) {
        return jdbcTemplate.query("""
                        SELECT
                            event_id,
                            payment_id,
                            booking_id,
                            provider_transaction_id,
                            provider_refund_transaction_id,
                            amount,
                            currency_code,
                            payload
                        FROM payment_refund_confirmations
                        WHERE event_id = ?
                           OR payment_id = ?
                        """,
                (resultSet, rowNumber) ->
                        new StoredRefundConfirmation(
                                resultSet.getObject("event_id", UUID.class),
                                resultSet.getObject("payment_id", UUID.class),
                                resultSet.getObject("booking_id", UUID.class),
                                resultSet.getString("provider_transaction_id"),
                                resultSet.getString("provider_refund_transaction_id"),
                                resultSet.getBigDecimal("amount"),
                                resultSet.getString("currency_code"),
                                resultSet.getString("payload")
                        ), eventId, paymentId);
    }

    public record StoredRefundConfirmation(
            UUID eventId,
            UUID paymentId,
            UUID bookingId,
            String providerTransactionId,
            String providerRefundTransactionId,
            BigDecimal amount,
            String currencyCode,
            String payload
    ) {
    }
}
