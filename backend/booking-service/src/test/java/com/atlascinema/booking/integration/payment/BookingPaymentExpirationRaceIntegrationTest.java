
package com.atlascinema.booking.integration.payment;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.outbox.OutboxEvent;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import com.atlascinema.booking.payment.event.PaymentResultEvent;
import com.atlascinema.booking.payment.event.PaymentResultEventType;
import com.atlascinema.booking.payment.service.BookingPaymentResultService;
import com.atlascinema.booking.payment.service.PaymentInboxService;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.repository.ConfirmedSeatRepository;
import com.atlascinema.booking.service.BookingExpirationScheduler;
import com.atlascinema.booking.service.RedisSeatHoldService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/*
 * Verifies concurrent payment confirmation and booking expiration
 * Uses real PostgreSQL row locks and Redis seat holds
 */
class BookingPaymentExpirationRaceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant START_TIME = Instant.parse("2030-01-10T12:00:00Z");

    private static final BigDecimal PRICE = new BigDecimal("29.90");

    private final AtomicReference<Instant> currentTime = new AtomicReference<>(START_TIME);

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ConfirmedSeatRepository confirmedSeatRepository;

    @Autowired
    private BookingPaymentResultService bookingPaymentResultService;

    @Autowired
    private PaymentInboxService paymentInboxService;

    @Autowired
    private BookingExpirationScheduler bookingExpirationScheduler;

    @Autowired
    private RedisSeatHoldService redisSeatHoldService;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private Clock clock;

    private BookingFixture bookingFixture;

    @BeforeEach
    void setUp() {
        currentTime.set(START_TIME);

        when(clock.instant()).thenAnswer(invocation -> currentTime.get());

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void cleanUpRedisHold() {
        if (bookingFixture == null) {
            return;
        }

        redisSeatHoldService.releaseSeat(
                bookingFixture.booking().getScreeningId(),
                bookingFixture.seatId(),
                bookingFixture.booking().getId());
    }

    @Test
    void paymentConfirmation_shouldWin_whenItLocksBookingBeforeExpiration()
            throws Exception {

        bookingFixture = createHeldBooking();

        UUID paymentEventId = recordSuccessfulPaymentEvent(bookingFixture.booking());

        CountDownLatch paymentProcessed = new CountDownLatch(1);
        CountDownLatch allowPaymentCommit = new CountDownLatch(1);

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(transactionManager);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            // Transaction A confirms the booking but delays its commit
            Future<Boolean> paymentFuture = executor.submit(() ->
                    transactionTemplate.execute(status -> {
                        boolean processed = bookingPaymentResultService.processEvent(
                                paymentEventId);

                        paymentProcessed.countDown();

                        awaitRelease(allowPaymentCommit);

                        return processed;
                    })
            );

            try {
                assertThat(paymentProcessed.await(10, TimeUnit.SECONDS))
                        .isTrue();

                // The payment was processed before the booking deadline
                // but its PostgreSQL transaction is still open
                currentTime.set(START_TIME.plusSeconds(10));

                CompletableFuture<Integer> expirationBackendPid =
                        new CompletableFuture<>();

                // Transaction B tries to expire the same booking
                Future<?> expirationFuture = executor.submit(() ->
                        transactionTemplate.executeWithoutResult(status -> {
                            expirationBackendPid.complete(currentPostgresBackendPid());

                            bookingExpirationScheduler.expireBookings();
                        })
                );

                // verify that PostgreSQL really blocked the expiration query
                awaitPostgresLockWait(
                        expirationBackendPid.get(10, TimeUnit.SECONDS));

                // Transaction A is allowed to commit first
                allowPaymentCommit.countDown();

                assertThat(paymentFuture.get(15, TimeUnit.SECONDS))
                        .isTrue();

                expirationFuture.get(15, TimeUnit.SECONDS);

            } finally {
                allowPaymentCommit.countDown();
            }
        }

        Booking persistedBooking = bookingRepository
                .findById(bookingFixture.booking().getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        assertThat(confirmedSeatRepository.existsByScreeningIdAndSeatIdIn(
                persistedBooking.getScreeningId(),
                List.of(bookingFixture.seatId())
        )).isTrue();

        assertThat(isInboxEventProcessed(paymentEventId))
                .isTrue();

        // the expiration scheduler must not overwrite CONFIRMED
        assertThat(findBookingOutboxEventTypes(persistedBooking.getId()))
                .doesNotContain("BookingExpired", "PaymentRefundRequested");
    }

    @Test
    void expiration_shouldWin_whenItLocksBookingBeforePaymentProcessing()
            throws Exception {

        bookingFixture = createHeldBooking();

        UUID paymentEventId = recordSuccessfulPaymentEvent(bookingFixture.booking());

        // move application time past the booking expiration deadline
        currentTime.set(START_TIME.plusSeconds(10));

        CountDownLatch expirationApplied = new CountDownLatch(1);
        CountDownLatch allowExpirationCommit = new CountDownLatch(1);

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            // Transaction A expires the booking but delays its commit
            Future<?> expirationFuture = executor.submit(() ->
                    transactionTemplate.executeWithoutResult(status -> {
                        bookingExpirationScheduler.expireBookings();

                        expirationApplied.countDown();

                        awaitRelease(allowExpirationCommit);
                    })
            );

            try {
                assertThat(expirationApplied.await(10, TimeUnit.SECONDS))
                        .isTrue();

                CompletableFuture<Integer> paymentBackendPid =
                        new CompletableFuture<>();

                // Transaction B processes the successful payment
                Future<Boolean> paymentFuture = executor.submit(() ->
                        transactionTemplate.execute(status -> {
                            paymentBackendPid.complete(currentPostgresBackendPid());

                            return bookingPaymentResultService.processEvent(paymentEventId);
                        })
                );

                // the payment processor must wait for the booking row lock
                awaitPostgresLockWait(
                        paymentBackendPid.get(10, TimeUnit.SECONDS));

                // Commit expiration before payment processing continues
                allowExpirationCommit.countDown();

                expirationFuture.get(15, TimeUnit.SECONDS);

                assertThat(paymentFuture.get(15, TimeUnit.SECONDS))
                        .isTrue();

            } finally {
                allowExpirationCommit.countDown();
            }
        }

        Booking persistedBooking = bookingRepository
                .findById(bookingFixture.booking().getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus())
                .isEqualTo(BookingStatus.EXPIRED);

        // No permanent seat may be allocated after the booking expires
        assertThat(confirmedSeatRepository.existsByScreeningIdAndSeatIdIn(
                persistedBooking.getScreeningId(),
                List.of(bookingFixture.seatId())
        )).isFalse();

        assertThat(isInboxEventProcessed(paymentEventId)).isTrue();

        // Successful payment cannot disappear after booking expiration
        // It must produce a refund request through the booking outbox
        assertThat(findBookingOutboxEventTypes(persistedBooking.getId()))
                .containsExactlyInAnyOrder(
                        "BookingExpired",
                        "PaymentRefundRequested");
    }

    /*
     * Creates a booking with a short business deadline and a longer Redis TTL
     * This lets the test control expiration without waiting for real time
     */
    private BookingFixture createHeldBooking() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        Booking booking = Booking.create(
                null,
                "payment-race@example.com",
                screeningId,
                toLocalDateTime(START_TIME.minusSeconds(60)));

        booking.addSeat(seatId, PRICE);

        booking.markAsHeld(
                toLocalDateTime(START_TIME.plusSeconds(5)));

        Booking persistedBooking =
                bookingRepository.saveAndFlush(booking);

        boolean acquired = redisSeatHoldService.holdSeat(
                screeningId,
                seatId,
                persistedBooking.getId(),
                Duration.ofMinutes(5));

        assertThat(acquired).isTrue();

        return new BookingFixture(persistedBooking, seatId);
    }

    private UUID recordSuccessfulPaymentEvent(Booking booking) {
        UUID paymentId = UUID.randomUUID();

        PaymentResultEvent event = new PaymentResultEvent(
                UUID.randomUUID(),
                PaymentResultEventType.PAYMENT_SUCCEEDED,
                paymentId,
                booking.getId(),
                PRICE,
                "PLN",
                "SIM-" + paymentId,
                START_TIME);

        boolean recorded = paymentInboxService.recordPaymentResult(
                event,
                jsonMapper.writeValueAsString(event));

        assertThat(recorded).isTrue();

        return event.eventId();
    }

    /*
     * gets the PostgreSQL connection id belonging to the current transaction
     */
    private int currentPostgresBackendPid() {
        Integer backendPid = jdbcTemplate.queryForObject(
                "SELECT pg_backend_pid()",
                Integer.class);

        if (backendPid == null) {
            throw new IllegalStateException("PostgreSQL backend PID was not available");
        }

        return backendPid;
    }

    /*
     * Observes a real blocked PostgreSQL query instead of guessing from timing
     */
    private void awaitPostgresLockWait(int backendPid)
            throws InterruptedException {

        long deadline =
                System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

        while (System.nanoTime() < deadline) {
            Boolean waitingForLock = jdbcTemplate.queryForObject(
                    """
                            SELECT EXISTS (
                                SELECT 1
                                FROM pg_stat_activity
                                WHERE pid = ?
                                  AND wait_event_type = 'Lock'
                            )
                            """,
                    Boolean.class,
                    backendPid
            );

            if (Boolean.TRUE.equals(waitingForLock)) {
                return;
            }

            Thread.sleep(25);
        }

        throw new AssertionError(
                "PostgreSQL transaction " + backendPid
                        + " did not wait for a row lock");
    }

    private boolean isInboxEventProcessed(UUID eventId) {
        Boolean processed = jdbcTemplate.queryForObject(
                """
                        SELECT processed_at IS NOT NULL
                        FROM payment_inbox_events
                        WHERE event_id = ?
                        """,
                Boolean.class,
                eventId);

        return Boolean.TRUE.equals(processed);
    }

    private List<String> findBookingOutboxEventTypes(UUID bookingId) {
        return outboxEventRepository.findAll().stream()
                .filter(event ->
                        bookingId.equals(event.getAggregateId()))
                .map(OutboxEvent::getEventType)
                .toList();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static void awaitRelease(CountDownLatch releaseLatch) {
        try {
            if (!releaseLatch.await(20, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for transaction release");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();

            throw new AssertionError("Transaction wait was interrupted",
                    exception);
        }
    }

    private record BookingFixture(Booking booking, UUID seatId) {
    }
}
