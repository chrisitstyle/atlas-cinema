
package com.atlascinema.payment.integration.refund;

import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.refund.event.BookingEventEnvelope;
import com.atlascinema.payment.refund.event.PaymentRefundRequestedPayload;
import com.atlascinema.payment.refund.listener.PaymentRefundRequestedKafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies durable and idempotent refund event reception
 * Uses PostgreSQL while invoking the listener without a Kafka broker
 */
class PaymentRefundInboxIntegrationTest extends AbstractIntegrationTest {

    private static final String BOOKING_EVENTS_TOPIC = "booking-events";

    private static final Instant EVENT_TIME = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired
    private PaymentRefundRequestedKafkaListener refundListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void onBookingEvent_shouldPersistRefundRequest_whenEventIsValid() {
        UUID paymentId = UUID.randomUUID();

        BookingEventEnvelope event = createRefundEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                paymentId,
                "BOOKING_NOT_CONFIRMABLE");

        receiveEvent(event);

        assertThat(countRefundEvents()).isEqualTo(1);

        String storedProviderTransactionId = jdbcTemplate.queryForObject(
                """
                SELECT provider_transaction_id
                FROM payment_refund_inbox_events
                WHERE event_id = ?
                """,
                String.class,
                event.eventId());

        assertThat(storedProviderTransactionId).isEqualTo("SIM-" + paymentId);

        Boolean waitingForProcessing = jdbcTemplate.queryForObject(
                """
                SELECT processed_at IS NULL
                FROM payment_refund_inbox_events
                WHERE event_id = ?
                """,
                Boolean.class,
                event.eventId());

        assertThat(waitingForProcessing).isTrue();
    }

    @Test
    void onBookingEvent_shouldIgnoreDuplicate_whenSameEventArrivesTwice() {
        BookingEventEnvelope event = createRefundEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BOOKING_NOT_CONFIRMABLE");

        receiveEvent(event);
        receiveEvent(event);

        assertThat(countRefundEvents()).isEqualTo(1);
    }

    @Test
    void onBookingEvent_shouldIgnoreUnrelatedBookingEvents() {
        BookingEventEnvelope event = new BookingEventEnvelope(
                UUID.randomUUID(),
                "BookingCancelled",
                UUID.randomUUID(),
                EVENT_TIME,
                jsonMapper.valueToTree(
                        Map.of("screeningId", UUID.randomUUID().toString())));

        receiveEvent(event);

        assertThat(countRefundEvents()).isZero();
    }

    @Test
    void onBookingEvent_shouldRejectEvent_whenKafkaKeyIsIncorrect() {
        BookingEventEnvelope event = createRefundEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BOOKING_NOT_CONFIRMABLE");

        String eventJson = jsonMapper.writeValueAsString(event);

        ConsumerRecord<String, String> kafkaRecord = new ConsumerRecord<>(
                        BOOKING_EVENTS_TOPIC,
                        0,
                        0L,
                        UUID.randomUUID().toString(),
                        eventJson);

        assertThatThrownBy(() -> refundListener.onBookingEvent(kafkaRecord))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(countRefundEvents()).isZero();
    }

    @Test
    void onBookingEvent_shouldRejectConflictingPayload_whenEventIdIsReused() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        BookingEventEnvelope originalEvent = createRefundEvent(
                eventId,
                bookingId,
                paymentId,
                "BOOKING_NOT_CONFIRMABLE");

        receiveEvent(originalEvent);

        BookingEventEnvelope conflictingEvent = createRefundEvent(
                eventId,
                bookingId,
                paymentId,
                "DIFFERENT_REASON");

        assertThatThrownBy(() -> receiveEvent(conflictingEvent)).isInstanceOf(IllegalStateException.class);

        assertThat(countRefundEvents()).isEqualTo(1);
    }

    @Test
    void onBookingEvent_shouldStoreDifferentEventsForSamePayment() {
        UUID bookingId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        BookingEventEnvelope firstEvent = createRefundEvent(
                UUID.randomUUID(),
                bookingId,
                paymentId,
                "BOOKING_NOT_CONFIRMABLE"
        );

        BookingEventEnvelope secondEvent = createRefundEvent(
                UUID.randomUUID(),
                bookingId,
                paymentId,
                "BOOKING_NOT_CONFIRMABLE"
        );

        receiveEvent(firstEvent);
        receiveEvent(secondEvent);

        // The inbox deduplicates event ids, not payment ids
        // The future refund processor will enforce payment-level idempotency
        assertThat(countRefundEvents()).isEqualTo(2);
    }

    private void receiveEvent(BookingEventEnvelope event) {
        String eventJson = jsonMapper.writeValueAsString(event);

        ConsumerRecord<String, String> kafkaRecord = new ConsumerRecord<>(
                        BOOKING_EVENTS_TOPIC,
                        0,
                        0L,
                        event.bookingId().toString(),
                        eventJson);

        refundListener.onBookingEvent(kafkaRecord);
    }

    private BookingEventEnvelope createRefundEvent(
            UUID eventId,
            UUID bookingId,
            UUID paymentId,
            String reason
    ) {
        PaymentRefundRequestedPayload payload = new PaymentRefundRequestedPayload(
                        paymentId,
                        "SIM-" + paymentId,
                        reason);

        return new BookingEventEnvelope(
                eventId,
                "PaymentRefundRequested",
                bookingId,
                EVENT_TIME,
                jsonMapper.valueToTree(payload));
    }

    private long countRefundEvents() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_refund_inbox_events",
                Long.class);

        return count;
    }
}
