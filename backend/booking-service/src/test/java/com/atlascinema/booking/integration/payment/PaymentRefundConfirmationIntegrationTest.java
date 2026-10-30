
package com.atlascinema.booking.integration.payment;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.event.BookingEventMessage;
import com.atlascinema.booking.event.PaymentRefundRequestedPayload;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.outbox.OutboxEvent;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import com.atlascinema.booking.payment.event.PaymentRefundedEvent;
import com.atlascinema.booking.payment.listener.PaymentRefundedKafkaListener;
import com.atlascinema.booking.repository.BookingRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies durable and idempotent completion of payment compensations
 * Uses real PostgreSQL and the existing booking outbox
 */
class PaymentRefundConfirmationIntegrationTest extends AbstractIntegrationTest {

    private static final String REFUNDS_TOPIC = "payment-refunds";
    private static final String REFUNDED_EVENT_TYPE = "PAYMENT_REFUNDED";

    private static final BigDecimal PRICE = new BigDecimal("29.90");

    private static final Instant EVENT_TIME = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired
    private PaymentRefundedKafkaListener refundListener;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void onPaymentRefunded_shouldStoreConfirmation_whenRefundWasRequested() {
        RefundFixture fixture = createRefundFixture();

        PaymentRefundedEvent event = createRefundedEvent(fixture);

        receiveEvent(event);

        assertThat(countConfirmations()).isEqualTo(1);

        String storedRefundReference = jdbcTemplate.queryForObject(
                """
                SELECT provider_refund_transaction_id
                FROM payment_refund_confirmations
                WHERE payment_id = ?
                """,
                String.class,
                fixture.paymentId());

        assertThat(storedRefundReference)
                .isEqualTo("SIM-REF-" + fixture.paymentId());

        // a refund does not turn the booking into a confirmed reservation
        assertThat(bookingRepository.findById(fixture.booking().getId()))
                .isPresent();
    }

    @Test
    void onPaymentRefunded_shouldIgnoreDuplicate_whenSameEventArrivesTwice() {
        RefundFixture fixture = createRefundFixture();
        PaymentRefundedEvent event = createRefundedEvent(fixture);

        receiveEvent(event);
        receiveEvent(event);

        assertThat(countConfirmations()).isEqualTo(1);
    }

    @Test
    void onPaymentRefunded_shouldAcceptSameRefundWithDifferentEventId() {
        RefundFixture fixture = createRefundFixture();

        PaymentRefundedEvent firstEvent = createRefundedEvent(fixture);

        PaymentRefundedEvent secondEvent = createRefundedEventWithId(
                        fixture,
                        UUID.randomUUID(),
                        "SIM-REF-" + fixture.paymentId(),
                        PRICE);

        receiveEvent(firstEvent);
        receiveEvent(secondEvent);

        // both messages report the same business refund
        assertThat(countConfirmations()).isEqualTo(1);
    }

    @Test
    void onPaymentRefunded_shouldRejectConflictingPayload_forSameEventId() {
        RefundFixture fixture = createRefundFixture();

        PaymentRefundedEvent originalEvent = createRefundedEvent(fixture);

        receiveEvent(originalEvent);

        PaymentRefundedEvent conflictingEvent = createRefundedEventWithId(
                        fixture,
                        originalEvent.eventId(),
                        "SIM-REF-CONFLICTING",
                        PRICE);

        assertThatThrownBy(() -> receiveEvent(conflictingEvent))
                .isInstanceOf(IllegalStateException.class);

        assertThat(countConfirmations()).isEqualTo(1);
    }

    @Test
    void onPaymentRefunded_shouldRejectEvent_whenKafkaKeyIsIncorrect() {
        RefundFixture fixture = createRefundFixture();
        PaymentRefundedEvent event = createRefundedEvent(fixture);

        String json = jsonMapper.writeValueAsString(event);

        ConsumerRecord<String, String> kafkaRecord = new ConsumerRecord<>(
                        REFUNDS_TOPIC,
                        0,
                        0L,
                        UUID.randomUUID().toString(),
                        json);

        assertThatThrownBy(() ->
                refundListener.onPaymentRefunded(kafkaRecord)
        ).isInstanceOf(IllegalArgumentException.class);

        assertThat(countConfirmations()).isZero();
    }

    @Test
    void onPaymentRefunded_shouldRejectEvent_whenRefundWasNeverRequested() {
        Booking booking = createBooking();

        UUID paymentId = UUID.randomUUID();

        PaymentRefundedEvent event = new PaymentRefundedEvent(
                UUID.randomUUID(),
                REFUNDED_EVENT_TYPE,
                paymentId,
                booking.getId(),
                PRICE,
                "PLN",
                "SIM-" + paymentId,
                "SIM-REF-" + paymentId,
                EVENT_TIME);

        assertThatThrownBy(() -> receiveEvent(event))
                .isInstanceOf(IllegalStateException.class);

        assertThat(countConfirmations()).isZero();
    }

    @Test
    void onPaymentRefunded_shouldRejectEvent_whenOriginalChargeDoesNotMatch() {
        RefundFixture fixture = createRefundFixture();

        PaymentRefundedEvent event = new PaymentRefundedEvent(
                UUID.randomUUID(),
                REFUNDED_EVENT_TYPE,
                fixture.paymentId(),
                fixture.booking().getId(),
                PRICE,
                "PLN",
                "SIM-WRONG-TRANSACTION",
                "SIM-REF-" + fixture.paymentId(),
                EVENT_TIME);

        assertThatThrownBy(() -> receiveEvent(event))
                .isInstanceOf(IllegalStateException.class);

        assertThat(countConfirmations()).isZero();
    }

    @Test
    void onPaymentRefunded_shouldRejectEvent_whenAmountDoesNotMatchBooking() {
        RefundFixture fixture = createRefundFixture();

        PaymentRefundedEvent event = createRefundedEventWithId(
                fixture,
                UUID.randomUUID(),
                "SIM-REF-" + fixture.paymentId(),
                new BigDecimal("39.90"));

        assertThatThrownBy(() -> receiveEvent(event)).isInstanceOf(IllegalStateException.class);

        assertThat(countConfirmations()).isZero();
    }

    private RefundFixture createRefundFixture() {
        Booking booking = createBooking();
        UUID paymentId = UUID.randomUUID();

        String providerTransactionId = "SIM-" + paymentId;

        PaymentRefundRequestedPayload requestPayload = new PaymentRefundRequestedPayload(
                        paymentId,
                        providerTransactionId,
                        "BOOKING_NOT_CONFIRMABLE");

        BookingEventMessage requestMessage = new BookingEventMessage(
                UUID.randomUUID(),
                "PaymentRefundRequested",
                booking.getId(),
                EVENT_TIME,
                jsonMapper.valueToTree(requestPayload));

        OutboxEvent requestOutboxEvent = OutboxEvent.create(
                requestMessage.eventId(),
                booking.getId(),
                requestMessage.eventType(),
                jsonMapper.writeValueAsString(requestMessage),
                LocalDateTime.ofInstant(EVENT_TIME, ZoneOffset.UTC));

        outboxEventRepository.saveAndFlush(requestOutboxEvent);

        return new RefundFixture(booking, paymentId);
    }

    private Booking createBooking() {
        Booking booking = Booking.create(
                null,
                "refund-confirmation@example.com",
                UUID.randomUUID(),
                LocalDateTime.ofInstant(
                        EVENT_TIME.minusSeconds(60),
                        ZoneOffset.UTC));

        booking.addSeat(UUID.randomUUID(), PRICE);

        booking.markAsHeld(
                LocalDateTime.ofInstant(
                        EVENT_TIME.plusSeconds(600),
                        ZoneOffset.UTC));

        return bookingRepository.saveAndFlush(booking);
    }

    private PaymentRefundedEvent createRefundedEvent(
            RefundFixture fixture
    ) {
        return createRefundedEventWithId(
                fixture,
                UUID.randomUUID(),
                "SIM-REF-" + fixture.paymentId(),
                PRICE);
    }

    private PaymentRefundedEvent createRefundedEventWithId(
            RefundFixture fixture,
            UUID eventId,
            String providerRefundTransactionId,
            BigDecimal amount
    ) {
        return new PaymentRefundedEvent(
                eventId,
                REFUNDED_EVENT_TYPE,
                fixture.paymentId(),
                fixture.booking().getId(),
                amount,
                "PLN",
                "SIM-" + fixture.paymentId(),
                providerRefundTransactionId,
                EVENT_TIME);
    }

    private void receiveEvent(PaymentRefundedEvent event) {
        String eventJson = jsonMapper.writeValueAsString(event);

        ConsumerRecord<String, String> kafkaRecord = new ConsumerRecord<>(
                        REFUNDS_TOPIC,
                        0,
                        0L,
                        event.bookingId().toString(),
                        eventJson);

        refundListener.onPaymentRefunded(kafkaRecord);
    }

    private long countConfirmations() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_refund_confirmations",
                Long.class);

        return count;
    }

    private record RefundFixture(
            Booking booking,
            UUID paymentId) {
    }
}
