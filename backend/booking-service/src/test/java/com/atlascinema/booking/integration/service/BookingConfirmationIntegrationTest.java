package com.atlascinema.booking.integration.service;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.repository.ConfirmedSeatRepository;
import com.atlascinema.booking.service.BookingConfirmationService;
import com.atlascinema.booking.service.RedisSeatHoldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/*
 * Verifies permanent seat allocation using PostgreSQL and Redis
 */
class BookingConfirmationIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ConfirmedSeatRepository confirmedSeatRepository;

    @Autowired
    private BookingConfirmationService bookingConfirmationService;

    @Autowired
    private RedisSeatHoldService redisSeatHoldService;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void confirmPaidBooking_shouldPersistConfirmedSeats_whenBookingOwnsValidHolds() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Booking booking = persistHeldBooking(screeningId, seatId, now().plusMinutes(10));
        assertThat(redisSeatHoldService.holdSeat(
                screeningId, seatId, booking.getId(), java.time.Duration.ofMinutes(10)
        )).isTrue();

        BookingResponse confirmedBooking = bookingConfirmationService.confirmPaidBooking(
                booking.getId());

        assertThat(confirmedBooking.status()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);
        assertThat(confirmedSeatRepository.existsByScreeningIdAndSeatIdIn(
                screeningId, List.of(seatId)
        )).isTrue();
    }

    @Test
    void confirmPaidBooking_shouldRejectBooking_whenExpirationHasPassed() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Booking booking = persistHeldBooking(screeningId, seatId, now().minusSeconds(1));

        assertThatThrownBy(() -> bookingConfirmationService.confirmPaidBooking(booking.getId()))
                .isInstanceOf(IllegalStateException.class);

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.HELD);
        assertThat(confirmedSeatRepository.count()).isZero();
    }

    @Test
    void confirmPaidBooking_shouldRejectBooking_whenRedisHoldIsMissing() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Booking booking = persistHeldBooking(screeningId, seatId, now().plusMinutes(10));

        assertThatThrownBy(() -> bookingConfirmationService.confirmPaidBooking(booking.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis seat holds");

        assertThat(bookingRepository.findById(booking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.HELD);
        assertThat(confirmedSeatRepository.count()).isZero();
    }

    @Test
    void confirmPaidBooking_shouldRollback_whenSeatWasAlreadySold() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        Booking firstBooking = persistHeldBooking(screeningId, seatId, now().plusMinutes(10));
        Booking secondBooking = persistHeldBooking(screeningId, seatId, now().plusMinutes(10));

        assertThat(redisSeatHoldService.holdSeat(
                screeningId, seatId, firstBooking.getId(), java.time.Duration.ofMinutes(10)
        )).isTrue();

        bookingConfirmationService.confirmPaidBooking(firstBooking.getId());

        redisSeatHoldService.releaseSeat(screeningId, seatId, firstBooking.getId());
        assertThat(redisSeatHoldService.holdSeat(
                screeningId, seatId, secondBooking.getId(), java.time.Duration.ofMinutes(10)
        )).isTrue();

        assertThatThrownBy(() -> bookingConfirmationService.confirmPaidBooking(secondBooking.getId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(bookingRepository.findById(secondBooking.getId()).orElseThrow().getStatus())
                .isEqualTo(BookingStatus.HELD);
        assertThat(confirmedSeatRepository.count()).isEqualTo(1);
    }

    private Booking persistHeldBooking(
            UUID screeningId,
            UUID seatId,
            LocalDateTime expiresAt
    ) {
        Booking booking = Booking.create(
                null, "guest@example.com", screeningId, now().minusMinutes(1));
        booking.addSeat(seatId, new BigDecimal("29.90"));
        booking.markAsHeld(expiresAt);
        return bookingRepository.saveAndFlush(booking);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
