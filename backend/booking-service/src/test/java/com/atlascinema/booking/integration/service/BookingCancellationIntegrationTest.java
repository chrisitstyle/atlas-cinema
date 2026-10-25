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
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.repository.SeatHoldRepository;
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
    private SeatHoldRepository seatHoldRepository;

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

        when(cinemaClient.getSeat(hallId, seatId
        )).thenReturn(new SeatClientResponse(seatId, hallId));
    }

    @Test
    void cancelBooking_shouldReleaseSeat_whenBookingIsCancelled() {
        CreateBookingRequest firstRequest = new CreateBookingRequest(
                        UUID.randomUUID(),
                        "first@example.com",
                        screeningId,
                        List.of(seatId));

        BookingResponse firstBooking = bookingService.createBooking(firstRequest);

        assertThat(firstBooking.status()).isEqualTo(BookingStatus.HELD);

        assertThat(seatHoldRepository.count()).isEqualTo(1);

        BookingResponse cancelledBooking = bookingService.cancelBooking(firstBooking.id());

        assertThat(cancelledBooking.status()).isEqualTo(BookingStatus.CANCELLED);

        assertThat(seatHoldRepository.count()).isZero();

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

        assertThat(seatHoldRepository.count()).isEqualTo(1);

        assertThat(bookingRepository.count()).isEqualTo(2);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
