
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/*
 * Verifies payment saga decisions with real PostgreSQL and Redis
 * Ensures booking changes, inbox processing and refunds remain consistent
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

        boolean processed = bookingPaymentResultService.processEvent(
                paymentEvent.eventId());

        assertThat(processed).isTrue();

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        // the booking must reference the payment that confirmed it
        assertThat(persistedBooking.getConfirmedPaymentId())
                .isEqualTo(paymentEvent.paymentId());

        assertThat(confirmedSeatRepository.existsByScreeningIdAndSeatIdIn(
                booking.getScreeningId(),
                List.of(seatId)
        )).isTrue();

        assertThat(confirmedSeatRepository.count()).isEqualTo(1);

        assertThat(isEventProcessed(paymentEvent.eventId())).isTrue();

        assertThat(outboxEventRepository.count()).isZero();

        // Permanent seats are protected by PostgreSQL
        // Temporary Redis holds are released after commit
        assertThat(redisSeatHoldService.areSeatsHeldByBooking(
                booking.getScreeningId(),
                List.of(seatId),
                booking.getId()
        )).isFalse();
    }

    @Test
    void processEvent_shouldKeepBookingHeld_whenPaymentFails() {
        Booking booking = createHeldBooking(now().plusMinutes(10));

        UUID seatId = booking.getSeats().getFirst().getSeatId();

        assertThat(redisSeatHoldService.holdSeat(
                booking.getScreeningId(),
                seatId,
                booking.getId(),
                Duration.ofMinutes(10)
        )).isTrue();

        PaymentResultEvent failedPayment = createPaymentEvent(booking,
                PaymentResultEventType.PAYMENT_FAILED);

        recordEvent(failedPayment);

        boolean processed = bookingPaymentResultService.processEvent(failedPayment.eventId());

        assertThat(processed).isTrue();

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus()).isEqualTo(BookingStatus.HELD);

        assertThat(persistedBooking.getConfirmedPaymentId()).isNull();

        // failed payment must not release the hold before booking expiration
        assertThat(redisSeatHoldService.areSeatsHeldByBooking(
                booking.getScreeningId(),
                List.of(seatId),
                booking.getId()
        )).isTrue();

        assertThat(confirmedSeatRepository.count()).isZero();

        assertThat(isEventProcessed(failedPayment.eventId())).isTrue();

        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void processEvent_shouldRequestRefund_whenBookingAlreadyExpired() {
        Booking booking = createHeldBooking(now().minusSeconds(1));

        PaymentResultEvent paymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        recordEvent(paymentEvent);

        boolean processed = bookingPaymentResultService.processEvent(paymentEvent.eventId());

        assertThat(processed).isTrue();

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(persistedBooking.getConfirmedPaymentId()).isNull();

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
    void processEvent_shouldConfirmBooking_whenSecondPaymentSucceedsAfterFirstFailure() {
        Booking booking = createHeldBooking(now().plusMinutes(10));

        UUID seatId = booking.getSeats().getFirst().getSeatId();

        assertThat(redisSeatHoldService.holdSeat(
                booking.getScreeningId(),
                seatId,
                booking.getId(),
                Duration.ofMinutes(10)
        )).isTrue();

        PaymentResultEvent failedPayment = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_FAILED);

        PaymentResultEvent successfulPayment = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        recordEvent(failedPayment);
        recordEvent(successfulPayment);

        bookingPaymentResultService.processEvent(failedPayment.eventId());

        // the failed attempt cannot cancel the booking
        assertThat(bookingRepository.findById(booking.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(BookingStatus.HELD);

        bookingPaymentResultService.processEvent(successfulPayment.eventId());

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        assertThat(persistedBooking.getConfirmedPaymentId())
                .isEqualTo(successfulPayment.paymentId());

        assertThat(confirmedSeatRepository.count()).isEqualTo(1);

        assertThat(isEventProcessed(failedPayment.eventId())).isTrue();

        assertThat(isEventProcessed(successfulPayment.eventId())).isTrue();

        // Only the successful payment funds the booking
        // Neither payment requires compensation
        assertThat(outboxEventRepository.count())
                .isZero();
    }

    @Test
    void processEvent_shouldKeepBookingConfirmed_whenFailedPaymentArrivesAfterSuccess() {
        Booking booking = createHeldBooking(now().plusMinutes(10));

        UUID seatId = booking.getSeats().getFirst().getSeatId();

        assertThat(redisSeatHoldService.holdSeat(
                booking.getScreeningId(),
                seatId,
                booking.getId(),
                Duration.ofMinutes(10)
        )).isTrue();

        PaymentResultEvent successfulPayment = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        PaymentResultEvent failedPayment = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_FAILED);

        recordEvent(successfulPayment);
        recordEvent(failedPayment);

        bookingPaymentResultService.processEvent(successfulPayment.eventId());

        bookingPaymentResultService.processEvent(failedPayment.eventId());

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        assertThat(persistedBooking.getConfirmedPaymentId())
                .isEqualTo(successfulPayment.paymentId());

        assertThat(confirmedSeatRepository.count())
                .isEqualTo(1);

        assertThat(isEventProcessed(successfulPayment.eventId()))
                .isTrue();

        assertThat(isEventProcessed(failedPayment.eventId()))
                .isTrue();

        // a different failed attempt cannot undo a successful booking
        assertThat(outboxEventRepository.count()).isZero();
    }

    @Test
    void processEvent_shouldNotProcessSameEventTwice() {
        Booking booking = createHeldBooking(now().minusSeconds(1));

        PaymentResultEvent paymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        recordEvent(paymentEvent);

        boolean firstResult = bookingPaymentResultService.processEvent(
                paymentEvent.eventId());

        boolean secondResult = bookingPaymentResultService.processEvent(
                paymentEvent.eventId());

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();

        // the same inbox event must create only one refund request
        assertThat(outboxEventRepository.count()).isEqualTo(1);

        assertThat(isEventProcessed(paymentEvent.eventId())).isTrue();
    }

    @Test
    void processEvent_shouldRequestRefund_whenRedisHoldIsMissing() {
        Booking booking = createHeldBooking(
                now().plusMinutes(10));

        PaymentResultEvent paymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        recordEvent(paymentEvent);

        boolean processed = bookingPaymentResultService.processEvent(
                paymentEvent.eventId());

        assertThat(processed).isTrue();

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus())
                .isEqualTo(BookingStatus.HELD);

        assertThat(persistedBooking.getConfirmedPaymentId()).isNull();

        assertThat(confirmedSeatRepository.count()).isZero();

        assertThat(isEventProcessed(paymentEvent.eventId())).isTrue();

        assertThat(outboxEventRepository.findAll())
                .singleElement()
                .extracting(OutboxEvent::getEventType)
                .isEqualTo("PaymentRefundRequested");
    }

    @Test
    void processEvent_shouldConfirmOnePaymentAndRefundTheOther_whenTwoPaymentsSucceedConcurrently()
            throws Exception {

        Booking booking = createHeldBooking(now().plusMinutes(10));

        UUID seatId = booking.getSeats().getFirst().getSeatId();

        assertThat(redisSeatHoldService.holdSeat(
                booking.getScreeningId(),
                seatId,
                booking.getId(),
                Duration.ofMinutes(10)
        )).isTrue();

        PaymentResultEvent firstPaymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        PaymentResultEvent secondPaymentEvent = createPaymentEvent(
                booking,
                PaymentResultEventType.PAYMENT_SUCCEEDED);

        // both events belong to the same booking but different payments
        assertThat(firstPaymentEvent.paymentId())
                .isNotEqualTo(secondPaymentEvent.paymentId());

        recordEvent(firstPaymentEvent);
        recordEvent(secondPaymentEvent);

        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch startProcessing = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            Future<Boolean> firstResult = executor.submit(() -> {
                workersReady.countDown();

                if (!startProcessing.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("First payment worker did not start");
                }

                return bookingPaymentResultService.processEvent(
                        firstPaymentEvent.eventId());
            });

            Future<Boolean> secondResult = executor.submit(() -> {
                workersReady.countDown();

                if (!startProcessing.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Second payment worker did not start");
                }

                return bookingPaymentResultService.processEvent(
                        secondPaymentEvent.eventId());
            });

            try {
                assertThat(workersReady.await(10, TimeUnit.SECONDS)).isTrue();

                // allow both workers to start processing concurrently
                startProcessing.countDown();

                assertThat(firstResult.get(20, TimeUnit.SECONDS))
                        .isTrue();

                assertThat(secondResult.get(20, TimeUnit.SECONDS))
                        .isTrue();

            } finally {
                startProcessing.countDown();
            }
        }

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        UUID confirmedPaymentId = persistedBooking.getConfirmedPaymentId();

        // Either payment may win the race
        assertThat(confirmedPaymentId)
                .isIn(firstPaymentEvent.paymentId(),
                        secondPaymentEvent.paymentId());

        // both payment events must be marked as processed
        assertThat(isEventProcessed(firstPaymentEvent.eventId())).isTrue();

        assertThat(isEventProcessed(secondPaymentEvent.eventId())).isTrue();

        // only one permanent seat allocation must exist
        assertThat(confirmedSeatRepository.count()).isEqualTo(1);

        List<OutboxEvent> refundEvents = outboxEventRepository
                .findAll()
                .stream()
                .filter(event ->
                        "PaymentRefundRequested".equals(
                                event.getEventType())).toList();

        // exactly one successful payment must be refunded
        assertThat(refundEvents).hasSize(1);

        UUID paymentToRefund = confirmedPaymentId.equals(firstPaymentEvent.paymentId())
                        ? secondPaymentEvent.paymentId()
                        : firstPaymentEvent.paymentId();

        BookingEventMessage refundMessage = jsonMapper.readValue(
                refundEvents.getFirst().getPayload(),
                BookingEventMessage.class);

        assertThat(refundMessage.bookingId()).isEqualTo(booking.getId());

        assertThat(refundMessage.payload().path("paymentId").asText())
                .isEqualTo(paymentToRefund.toString());

        assertThat(
                refundMessage.payload().path("providerTransactionId").asText()
        ).isEqualTo("SIM-" + paymentToRefund);
    }

    @Test
    void processEvent_shouldNotRefundConfirmedPayment_whenSameEventIsProcessedAgain() {
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

        boolean firstProcessing = bookingPaymentResultService.processEvent(
                        paymentEvent.eventId());

        boolean repeatedProcessing = bookingPaymentResultService.processEvent(
                        paymentEvent.eventId());

        assertThat(firstProcessing).isTrue();

        assertThat(repeatedProcessing).isFalse();

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        assertThat(persistedBooking.getConfirmedPaymentId())
                .isEqualTo(paymentEvent.paymentId());

        assertThat(confirmedSeatRepository.count()).isEqualTo(1);

        assertThat(isEventProcessed(paymentEvent.eventId())).isTrue();

        // reprocessing the successful payment must not request a refund
        assertThat(outboxEventRepository.findAll())
                .noneMatch(event -> "PaymentRefundRequested".equals(
                                event.getEventType()));
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

    private PaymentResultEvent createPaymentEvent(
            Booking booking,
            PaymentResultEventType eventType
    ) {
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
        boolean recorded = paymentInboxService.recordPaymentResult(
                paymentEvent,
                jsonMapper.writeValueAsString(paymentEvent)
        );

        assertThat(recorded).isTrue();
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
