package com.atlascinema.booking.integration.service;

import com.atlascinema.booking.client.CinemaClient;
import com.atlascinema.booking.client.ScreeningClient;
import com.atlascinema.booking.client.dto.ScreeningClientResponse;
import com.atlascinema.booking.client.dto.ScreeningClientStatus;
import com.atlascinema.booking.client.dto.SeatClientResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.exception.SeatAlreadyReservedException;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class BookingConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final int ATTEMPTS = 100;

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

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
                .thenReturn(
                        new ScreeningClientResponse(
                                screeningId,
                                hallId,
                                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
                                        .plusHours(2),
                                new BigDecimal("29.90"),
                                ScreeningClientStatus.SCHEDULED));

        when(cinemaClient.getSeat(
                hallId,
                seatId
        )).thenReturn(
                new SeatClientResponse(
                        seatId,
                        hallId));
    }

    @Test
    void createBooking_shouldAllowExactlyOneBooking_whenManyRequestsReserveSameSeatConcurrently() throws Exception {

        CreateBookingRequest request = new CreateBookingRequest(
                        null,
                        "guest@example.com",
                        screeningId,
                        List.of(seatId));

        CountDownLatch ready = new CountDownLatch(ATTEMPTS);

        CountDownLatch start = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(ATTEMPTS);

        List<Future<AttemptResult>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < ATTEMPTS; i++) {

                Future<AttemptResult> future = executor.submit(() -> {

                            ready.countDown();

                            start.await();

                            try {
                                bookingService.createBooking(request);

                                return AttemptResult.SUCCESS;

                            } catch (SeatAlreadyReservedException exception) {
                                return AttemptResult.CONFLICT;
                            }
                        });

                futures.add(future);
            }

            boolean allThreadsReady = ready.await(10, TimeUnit.SECONDS);

            assertThat(allThreadsReady).isTrue();

            start.countDown();

            List<AttemptResult> results = new ArrayList<>();

            for (Future<AttemptResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }

            long successes = results.stream()
                            .filter(result -> result == AttemptResult.SUCCESS)
                            .count();

            long conflicts = results.stream()
                            .filter(result -> result == AttemptResult.CONFLICT)
                            .count();

            assertThat(successes).isEqualTo(1);

            assertThat(conflicts).isEqualTo(ATTEMPTS - 1);

            assertThat(bookingRepository.count()).isEqualTo(1);

        } finally {
            executor.shutdownNow();
        }
    }

    private enum AttemptResult {
        SUCCESS,
        CONFLICT
    }
}
