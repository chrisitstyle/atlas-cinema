
package com.atlascinema.booking.integration.payment;

import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.payment.event.PaymentResultEvent;
import com.atlascinema.booking.payment.event.PaymentResultEventType;
import com.atlascinema.booking.payment.listener.PaymentResultKafkaListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies durable and idempotent payment event reception
 * Uses real PostgreSQL without publishing test messages to Kafka
 */
class PaymentInboxIntegrationTest extends AbstractIntegrationTest {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    @Autowired
    private PaymentResultKafkaListener paymentResultKafkaListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void onPaymentResult_shouldPersistEvent_whenMessageIsValid() {
        PaymentResultEvent paymentResultEvent = createPaymentResultEvent();

        receivePaymentEvent(paymentResultEvent);

        assertThat(countInboxEvents())
                .isEqualTo(1L);

        String storedEventType = jdbcTemplate.queryForObject(
                """
                SELECT event_type
                FROM payment_inbox_events
                WHERE event_id = ?
                """,
                String.class,
                paymentResultEvent.eventId());

        assertThat(storedEventType).isEqualTo("PAYMENT_SUCCEEDED");

        Long unprocessedEventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_inbox_events
                WHERE processed_at IS NULL
                """,
                Long.class);

        assertThat(unprocessedEventCount).isEqualTo(1L);
    }

    @Test
    void onPaymentResult_shouldIgnoreDuplicate_whenSameEventIsReceivedTwice() {
        PaymentResultEvent paymentResultEvent = createPaymentResultEvent();

        receivePaymentEvent(paymentResultEvent);
        receivePaymentEvent(paymentResultEvent);

        assertThat(countInboxEvents()).isEqualTo(1L);
    }

    @Test
    void onPaymentResult_shouldRejectMessage_whenKafkaKeyDoesNotMatchBookingId() {
        PaymentResultEvent paymentResultEvent = createPaymentResultEvent();

        String eventPayloadJson = jsonMapper.writeValueAsString(paymentResultEvent);

        ConsumerRecord<String, String> kafkaRecord = new ConsumerRecord<>(
                        PAYMENT_EVENTS_TOPIC,
                        0,
                        0L,
                        UUID.randomUUID().toString(),
                        eventPayloadJson);

        assertThatThrownBy(() -> paymentResultKafkaListener.onPaymentResult(kafkaRecord)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(countInboxEvents()).isZero();
    }

    @Test
    void onPaymentResult_shouldRejectConflictingPayload_whenEventIdIsReused() {
        PaymentResultEvent originalEvent = createPaymentResultEvent();

        receivePaymentEvent(originalEvent);

        PaymentResultEvent conflictingEvent = new PaymentResultEvent(
                originalEvent.eventId(),
                PaymentResultEventType.PAYMENT_FAILED,
                originalEvent.paymentId(),
                originalEvent.bookingId(),
                originalEvent.amount(),
                originalEvent.currencyCode(),
                originalEvent.providerTransactionId(),
                originalEvent.occurredAt());

        assertThatThrownBy(() -> receivePaymentEvent(conflictingEvent)
        ).isInstanceOf(IllegalStateException.class);

        assertThat(countInboxEvents()).isEqualTo(1L);
    }

    private void receivePaymentEvent(PaymentResultEvent paymentResultEvent) {
        String eventPayloadJson = jsonMapper.writeValueAsString(paymentResultEvent);

        ConsumerRecord<String, String> kafkaRecord = new ConsumerRecord<>(
                        PAYMENT_EVENTS_TOPIC,
                        0,
                        0L,
                        paymentResultEvent.bookingId().toString(),
                        eventPayloadJson);

        paymentResultKafkaListener.onPaymentResult(kafkaRecord);
    }

    private PaymentResultEvent createPaymentResultEvent() {
        UUID paymentId = UUID.randomUUID();

        return new PaymentResultEvent(
                UUID.randomUUID(),
                PaymentResultEventType.PAYMENT_SUCCEEDED,
                paymentId,
                UUID.randomUUID(),
                new BigDecimal("29.90"),
                "PLN",
                "SIM-" + paymentId,
                Instant.parse("2026-10-29T10:00:00Z"));
    }

    private long countInboxEvents() {
        Long eventCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_inbox_events",
                Long.class);

        return eventCount;
    }
}
