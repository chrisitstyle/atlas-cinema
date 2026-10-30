package com.atlascinema.booking.integration.service;

import com.atlascinema.booking.client.CinemaClient;
import com.atlascinema.booking.client.ScreeningClient;
import com.atlascinema.booking.client.dto.ScreeningClientResponse;
import com.atlascinema.booking.client.dto.ScreeningClientStatus;
import com.atlascinema.booking.client.dto.SeatClientResponse;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.outbox.OutboxEvent;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class BookingCancellationIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private ScreeningClient screeningClient;

    @MockitoBean
    private CinemaClient cinemaClient;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private Clock clock;

    private UUID screeningId;
    private UUID seatId;

    @BeforeEach
    void setUp() {
        screeningId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();
        seatId = UUID.randomUUID();

        when(clock.instant()).thenReturn(NOW);

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        when(screeningClient.getScreening(screeningId))
                .thenReturn(new ScreeningClientResponse(
                        screeningId,
                        hallId,
                        now().plusHours(2),
                        new BigDecimal("29.90"),
                        ScreeningClientStatus.SCHEDULED));

        when(cinemaClient.getSeat(hallId, seatId
        )).thenReturn(new SeatClientResponse(seatId, hallId));
    }

    @Test
    void cancelBooking_shouldReleaseSeatAndCreateOutboxEvent_whenBookingIsCancelled() {

        String key = "seat-hold:"
                + screeningId
                + ":"
                + seatId;

        CreateBookingRequest firstRequest = new CreateBookingRequest(
                UUID.randomUUID(),
                "first@example.com",
                screeningId,
                List.of(seatId));

        BookingResponse firstBooking = bookingService.createBooking(firstRequest);

        assertThat(firstBooking.status()).isEqualTo(BookingStatus.HELD);

        assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(firstBooking.id().toString());

        BookingResponse cancelledBooking = bookingService.cancelBooking(firstBooking.id());

        assertThat(cancelledBooking.status()).isEqualTo(BookingStatus.CANCELLED);

        assertThat(redisTemplate.hasKey(key))
                .isFalse();

        List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();

        assertThat(outboxEvents).hasSize(1);

        OutboxEvent outboxEvent = outboxEvents.getFirst();

        assertThat(outboxEvent.getAggregateId()).isEqualTo(firstBooking.id());

        assertThat(outboxEvent.getEventType()).isEqualTo("BookingCancelled");

        assertThat(outboxEvent.getPublishedAt()).isNull();

        assertThat(outboxEvent.getPayload()).contains(firstBooking.id().toString());

        CreateBookingRequest secondRequest = new CreateBookingRequest(
                UUID.randomUUID(),
                "second@example.com",
                screeningId,
                List.of(seatId));

        BookingResponse secondBooking = bookingService.createBooking(secondRequest);

        assertThat(secondBooking.status()).isEqualTo(BookingStatus.HELD);

        assertThat(secondBooking.seats()).hasSize(1);

        assertThat(secondBooking.seats()
                .getFirst()
                .seatId())
                .isEqualTo(seatId);

        assertThat(redisTemplate.opsForValue().get(key))
                .isEqualTo(secondBooking.id().toString());

        assertThat(bookingRepository.count()).isEqualTo(2);
    }

    @Test
    void cancelBooking_shouldNotReleaseSeatUntilOuterTransactionCommits() {
        BookingResponse booking = createHeldBooking();
        String key = redisHoldKey();

        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            BookingResponse cancelledBooking = bookingService.cancelBooking(booking.id());

            assertThat(cancelledBooking.status()).isEqualTo(BookingStatus.CANCELLED);

            // the database transaction has not committed yet
            assertThat(redisTemplate.opsForValue().get(key))
                    .isEqualTo(booking.id().toString());
        });

        // AFTER_COMMIT listener releases the hold only after the transaction succeeds
        assertThat(redisTemplate.hasKey(key)).isFalse();

        assertThat(bookingRepository
                .findById(booking.id())
                .orElseThrow()
                .getStatus())
                .isEqualTo(BookingStatus.CANCELLED);

        assertThat(outboxEventRepository.count()).isEqualTo(1);
    }

    @Test
    void cancelBooking_shouldKeepSeatHold_whenOuterTransactionRollsBack() {
        BookingResponse booking = createHeldBooking();
        String key = redisHoldKey();

        try {
            TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

            transactionTemplate.executeWithoutResult(status -> {
                BookingResponse cancelledBooking = bookingService.cancelBooking(booking.id());

                assertThat(cancelledBooking.status()).isEqualTo(BookingStatus.CANCELLED);

                assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(booking.id().toString());

                // simulate a failure later in the same database transaction
                status.setRollbackOnly();
            });

            assertThat(bookingRepository
                    .findById(booking.id())
                    .orElseThrow()
                    .getStatus())
                    .isEqualTo(BookingStatus.HELD);

            assertThat(outboxEventRepository.count()).isZero();

            assertThat(redisTemplate.opsForValue().get(key)).isEqualTo(booking.id().toString());

        } finally {
            // this test deliberately leaves a valid hold, so remove its fixture
            redisTemplate.delete(key);
        }
    }

    private BookingResponse createHeldBooking() {
        return bookingService.createBooking(new CreateBookingRequest(
                        UUID.randomUUID(),
                        "cancel-after-commit@example.com",
                        screeningId,
                        List.of(seatId)));
    }

    private String redisHoldKey() {
        return "seat-hold:" + screeningId + ":" + seatId;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
