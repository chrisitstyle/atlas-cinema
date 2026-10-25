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
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.service.BookingExpirationScheduler;
import com.atlascinema.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import java.time.LocalDateTime;
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
    private StringRedisTemplate redisTemplate;

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
        screeningId = UUID.randomUUID();
        hallId = UUID.randomUUID();
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

        when(cinemaClient.getSeat(hallId, seatId))
                .thenReturn(new SeatClientResponse(seatId, hallId));
    }

    @Test
    void expireBooking_shouldMarkBookingAsExpired_whenRedisHoldExpires()
            throws InterruptedException {

        String key = "seat-hold:"
                + screeningId
                + ":"
                + seatId;

        try {
            CreateBookingRequest request = new CreateBookingRequest(
                            UUID.randomUUID(),
                            "guest@example.com",
                            screeningId,
                            List.of(seatId));

            BookingResponse booking = bookingService.createBooking(request);

            assertThat(booking.status()).isEqualTo(BookingStatus.HELD);

            assertThat(redisTemplate.opsForValue().get(key)
            ).isEqualTo(booking.id().toString());

            waitUntilHoldExpires(key);

            assertThat(redisTemplate.hasKey(key))
                    .isFalse();

            // redis uses real time, but the application Clock is mocked
            when(clock.instant())
                    .thenReturn(NOW.plusSeconds(3));

            bookingExpirationScheduler.expireBookings();

            Booking expiredBooking = bookingRepository
                            .findById(booking.id())
                            .orElseThrow();

            assertThat(expiredBooking.getStatus())
                    .isEqualTo(BookingStatus.EXPIRED);

        } finally {
            redisTemplate.delete(key);
        }
    }

    private void waitUntilHoldExpires(
            String key
    ) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;

        while (Boolean.TRUE.equals(
                redisTemplate.hasKey(key)
        )) {
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError(
                        "Redis hold did not expire"
                );
            }

            Thread.sleep(50);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
