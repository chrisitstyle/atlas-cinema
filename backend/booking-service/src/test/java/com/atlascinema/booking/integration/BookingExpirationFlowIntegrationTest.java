package com.atlascinema.booking.integration;

import com.atlascinema.booking.client.CinemaClient;
import com.atlascinema.booking.client.ScreeningClient;
import com.atlascinema.booking.client.dto.ScreeningClientResponse;
import com.atlascinema.booking.client.dto.ScreeningClientStatus;
import com.atlascinema.booking.client.dto.SeatClientResponse;
import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.event.BookingEventMessage;
import com.atlascinema.booking.event.BookingEventType;
import com.atlascinema.booking.event.BookingExpiredPayload;
import com.atlascinema.booking.outbox.OutboxEvent;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.service.BookingExpirationScheduler;
import com.atlascinema.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = "booking.hold-duration=2s")
class BookingExpirationFlowIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingExpirationScheduler bookingExpirationScheduler;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private ScreeningClient screeningClient;

    @MockitoBean
    private CinemaClient cinemaClient;

    @MockitoBean
    private Clock clock;

    private UUID screeningId;
    private UUID hallId;
    private UUID seatId;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();

        screeningId = UUID.randomUUID();
        hallId = UUID.randomUUID();
        seatId = UUID.randomUUID();

        when(clock.instant()).thenReturn(NOW);

        when(clock.getZone()).thenReturn(ZoneOffset.UTC);

        when(screeningClient.getScreening(screeningId))
                .thenReturn(
                        new ScreeningClientResponse(
                                screeningId,
                                hallId,
                                now().plusHours(2),
                                new BigDecimal("29.90"),
                                ScreeningClientStatus.SCHEDULED));

        when(cinemaClient.getSeat(hallId, seatId))
                .thenReturn(new SeatClientResponse(seatId, hallId));
    }

    @Test
    void expireBooking_shouldMarkBookingAsExpiredAndCreateOutboxEvent_whenRedisHoldExpires()
            throws InterruptedException {

        String seatHoldKey = "seat-hold:"
                        + screeningId
                        + ":"
                        + seatId;

        try {
            UUID customerId = UUID.randomUUID();

            CreateBookingRequest createBookingRequest = new CreateBookingRequest(
                            customerId,
                            "guest@example.com",
                            screeningId,
                            List.of(seatId));

            BookingResponse createdBooking = bookingService.createBooking(createBookingRequest);

            assertThat(createdBooking.status()
            ).isEqualTo(BookingStatus.HELD);

            assertThat(redisTemplate
                            .opsForValue()
                            .get(seatHoldKey)
            ).isEqualTo(createdBooking.id().toString());

            waitUntilHoldExpires(seatHoldKey);

            assertThat(redisTemplate.hasKey(seatHoldKey)).isFalse();

            /*
             * Redis uses real time while the application Clock is mocked
             * so move the application time past the booking expiration time
             */
            Instant expirationProcessingTime = NOW.plusSeconds(3);

            when(clock.instant()).thenReturn(expirationProcessingTime);

            bookingExpirationScheduler.expireBookings();

            Booking expiredBooking = bookingRepository
                            .findById(createdBooking.id())
                            .orElseThrow();

            assertThat(expiredBooking.getStatus()
            ).isEqualTo(BookingStatus.EXPIRED);

            List<OutboxEvent> outboxEvents = outboxEventRepository.findAll();

            assertThat(outboxEvents).hasSize(1);

            OutboxEvent bookingExpiredOutboxEvent = outboxEvents.getFirst();

            assertThat(bookingExpiredOutboxEvent.getAggregateId()).isEqualTo(createdBooking.id());

            assertThat(bookingExpiredOutboxEvent.getEventType())
                    .isEqualTo(BookingEventType.BOOKING_EXPIRED.eventName());

            assertThat(bookingExpiredOutboxEvent.getPublishedAt()).isNull();

            BookingEventMessage bookingExpiredEventMessage = jsonMapper.readValue(
                            bookingExpiredOutboxEvent.getPayload(),
                            BookingEventMessage.class);

            assertThat(bookingExpiredEventMessage.eventId()).isEqualTo(bookingExpiredOutboxEvent.getId());

            assertThat(bookingExpiredEventMessage.eventType()).isEqualTo(BookingEventType.BOOKING_EXPIRED.eventName());

            assertThat(bookingExpiredEventMessage.bookingId()).isEqualTo(createdBooking.id());

            assertThat(bookingExpiredEventMessage.occurredAt()).isEqualTo(expirationProcessingTime);

            BookingExpiredPayload bookingExpiredPayload = jsonMapper.convertValue(
                            bookingExpiredEventMessage.payload(),
                            BookingExpiredPayload.class);

            assertThat(bookingExpiredPayload.customerId()).isEqualTo(customerId);

            assertThat(bookingExpiredPayload.contactEmail()).isEqualTo("guest@example.com");

            assertThat(bookingExpiredPayload.screeningId()).isEqualTo(screeningId);

        } finally {
            redisTemplate.delete(seatHoldKey);
        }
    }

    private void waitUntilHoldExpires(
            String seatHoldKey
    ) throws InterruptedException {
        long expirationWaitDeadline = System.currentTimeMillis() + 5_000;

        while (Boolean.TRUE.equals(
                redisTemplate.hasKey(seatHoldKey))) {
            if (System.currentTimeMillis() >= expirationWaitDeadline) {

                throw new AssertionError("Redis seat hold did not expire");
            }

            Thread.sleep(50);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}