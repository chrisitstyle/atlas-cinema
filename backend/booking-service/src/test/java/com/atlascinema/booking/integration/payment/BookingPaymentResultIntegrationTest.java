
package com.atlascinema.booking.integration.payment;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.event.BookingEventMessage;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.outbox.OutboxEvent;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import com.atlascinema.booking.payment.event.PaymentResultEvent;
import com.atlascinema.booking.payment.event.PaymentResultEventType;
import com.atlascinema.booking.payment.service.BookingPaymentResultService;
import com.atlascinema.booking.payment.service.PaymentInboxService;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.repository.ConfirmedSeatRepository;
import com.atlascinema.booking.service.RedisSeatHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/*
 * Verifies payment saga decisions with real PostgreSQL and Redis
 * Inbox completion and booking changes must commit together
 */
class BookingPaymentResultIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    private static final BigDecimal PRICE = new BigDecimal("29.90");

    @Autowired
    private BookingPaymentResultService bookingPaymentResultService;

    @Autowired
    private PaymentInboxService paymentInboxService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ConfirmedSeatRepository confirmedSeatRepository;

    @Autowired
    private RedisSeatHoldService redisSeatHoldService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void processEvent_shouldConfirmBooking_whenPaymentSucceedsAndHoldsAreValid() {
        Booking booking = createHeldBooking(now().plusMinutes(10));
        UUID seatId = booking.getSeats().getFirst().getSeatId();

        assertThat(redisSeatHoldService.holdSeat(
                booking.getScreeningId(),
                seatId,
                booking.getId(),
                Duration.ofMinutes(10)
        )).isTrue();

        PaymentResultEvent paymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        recordEvent(paymentEvent);

        boolean processed = bookingPaymentResultService.processEvent(paymentEvent.eventId());

        assertThat(processed).isTrue();

        assertThat(bookingRepository.findById(booking.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        assertThat(confirmedSeatRepository.existsByScreeningIdAndSeatIdIn(
                booking.getScreeningId(),
                java.util.List.of(seatId)
        )).isTrue();

        assertThat(isEventProcessed(paymentEvent.eventId())).isTrue();
        assertThat(outboxEventRepository.count()).isZero();

        // Confirmed seats remain permanently protected by PostgreSQL
        // Temporary Redis holds are released after commit
        assertThat(redisSeatHoldService.areSeatsHeldByBooking(
                booking.getScreeningId(),
                java.util.List.of(seatId),
                booking.getId()
        )).isFalse();
    }

    @Test
    void processEvent_shouldCancelBooking_whenPaymentFails() {
        Booking booking = createHeldBooking(now().plusMinutes(10));
        UUID seatId = booking.getSeats().getFirst().getSeatId();

        assertThat(redisSeatHoldService.holdSeat(
                booking.getScreeningId(),
                seatId,
                booking.getId(),
                Duration.ofMinutes(10)
        )).isTrue();

        PaymentResultEvent paymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_FAILED);

        recordEvent(paymentEvent);
        bookingPaymentResultService.processEvent(paymentEvent.eventId());

        assertThat(bookingRepository.findById(booking.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(BookingStatus.CANCELLED);

        assertThat(confirmedSeatRepository.count()).isZero();
        assertThat(isEventProcessed(paymentEvent.eventId())).isTrue();

        assertThat(redisSeatHoldService.areSeatsHeldByBooking(
                booking.getScreeningId(),
                java.util.List.of(seatId),
                booking.getId()
        )).isFalse();

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .extracting(OutboxEvent::getEventType)
                .isEqualTo("BookingCancelled");
    }

    @Test
    void processEvent_shouldRequestRefund_whenBookingAlreadyExpired() {
        Booking booking = createHeldBooking(now().minusSeconds(1));

        PaymentResultEvent paymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        recordEvent(paymentEvent);
        bookingPaymentResultService.processEvent(paymentEvent.eventId());

        assertThat(confirmedSeatRepository.count()).isZero();
        assertThat(isEventProcessed(paymentEvent.eventId())).isTrue();

        OutboxEvent refundEvent = outboxEventRepository
                .findAll()
                .getFirst();

        assertThat(refundEvent.getEventType()).isEqualTo("PaymentRefundRequested");

        assertThat(refundEvent.getAggregateId()).isEqualTo(booking.getId());

        BookingEventMessage message = jsonMapper.readValue(
                refundEvent.getPayload(),
                BookingEventMessage.class);

        assertThat(message.payload().path("paymentId").asText())
                .isEqualTo(paymentEvent.paymentId().toString());

        assertThat(message.payload().path("reason").asText())
                .isEqualTo("BOOKING_NOT_CONFIRMABLE");
    }

    @Test
    void processEvent_shouldNotProcessSameEventTwice() {
        Booking booking = createHeldBooking(now().minusSeconds(1));

        PaymentResultEvent paymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        recordEvent(paymentEvent);

        boolean firstResult = bookingPaymentResultService.processEvent(paymentEvent.eventId());

        boolean secondResult = bookingPaymentResultService.processEvent(paymentEvent.eventId());

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();

        // only one refund request may be created for this inbox event
        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void processEvent_shouldRequestRefund_whenRedisHoldIsMissing() {
        Booking booking = createHeldBooking(now().plusMinutes(10));

        PaymentResultEvent paymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        recordEvent(paymentEvent);
        bookingPaymentResultService.processEvent(paymentEvent.eventId());

        assertThat(bookingRepository.findById(booking.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(BookingStatus.HELD);

        assertThat(confirmedSeatRepository.count()).isZero();
        assertThat(isEventProcessed(paymentEvent.eventId())).isTrue();

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .extracting(OutboxEvent::getEventType)
                .isEqualTo("PaymentRefundRequested");
    }

    private Booking createHeldBooking(LocalDateTime expiresAt) {
        Booking booking = Booking.create(
                null,
                "payment-saga@example.com",
                UUID.randomUUID(),
                now().minusMinutes(1));

        booking.addSeat(UUID.randomUUID(), PRICE);
        booking.markAsHeld(expiresAt);

        return bookingRepository.saveAndFlush(booking);
    }

    private PaymentResultEvent createPaymentEvent(Booking booking, PaymentResultEventType eventType) {
        UUID paymentId = UUID.randomUUID();

        return new PaymentResultEvent(
                UUID.randomUUID(),
                eventType,
                paymentId,
                booking.getId(),
                PRICE,
                "PLN",
                "SIM-" + paymentId,
                NOW);
    }

    private void recordEvent(PaymentResultEvent paymentEvent) {
        paymentInboxService.recordPaymentResult(
                paymentEvent,
                jsonMapper.writeValueAsString(paymentEvent));
    }

    private boolean isEventProcessed(UUID eventId) {
        Integer processedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_inbox_events
                WHERE event_id = ?
                  AND processed_at IS NOT NULL
                """,
                Integer.class,
                eventId);

        return processedCount != null && processedCount == 1;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
