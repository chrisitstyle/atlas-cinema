
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
import java.time.Duration;
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
 * Ensures confirmed bookings are associated with the correct payment
 */
class BookingConfirmationIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    private static final BigDecimal PRICE = new BigDecimal("29.90");

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
        UUID paymentId = UUID.randomUUID();

        Booking booking = persistHeldBooking(
                screeningId,
                seatId,
                now().plusMinutes(10));

        assertThat(redisSeatHoldService.holdSeat(
                screeningId,
                seatId,
                booking.getId(),
                Duration.ofMinutes(10)
        )).isTrue();

        BookingResponse confirmedBooking = bookingConfirmationService.confirmPaidBooking(
                booking.getId(),
                paymentId);

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(confirmedBooking.status())
                .isEqualTo(BookingStatus.CONFIRMED);

        assertThat(persistedBooking.getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        // the booking must remember the payment responsible for confirmation
        assertThat(persistedBooking.getConfirmedPaymentId()).isEqualTo(paymentId);

        // seats must be permanently allocated in PostgreSQL
        assertThat(confirmedSeatRepository.existsByScreeningIdAndSeatIdIn(
                screeningId,
                List.of(seatId)
        )).isTrue();

        assertThat(confirmedSeatRepository.count()).isEqualTo(1);
    }

    @Test
    void confirmPaidBooking_shouldRejectBooking_whenExpirationHasPassed() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        Booking booking = persistHeldBooking(
                screeningId,
                seatId,
                now().minusSeconds(1));

        assertThatThrownBy(() -> bookingConfirmationService.confirmPaidBooking(booking.getId(),
                paymentId)
        ).isInstanceOf(IllegalStateException.class);

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        assertThat(persistedBooking.getStatus())
                .isEqualTo(BookingStatus.HELD);

        // an expired booking must not be associated with a payment
        assertThat(persistedBooking.getConfirmedPaymentId()).isNull();

        assertThat(confirmedSeatRepository.count()).isZero();
    }

    @Test
    void confirmPaidBooking_shouldRejectBooking_whenRedisHoldIsMissing() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();

        Booking booking = persistHeldBooking(
                screeningId,
                seatId,
                now().plusMinutes(10));

        // no Redis hold is created for this booking
        assertThatThrownBy(() -> bookingConfirmationService.confirmPaidBooking(
                booking.getId(),
                paymentId)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Redis seat holds");

        Booking persistedBooking = bookingRepository
                .findById(booking.getId())
                .orElseThrow();

        // the transaction must roll back the attempted confirmation
        assertThat(persistedBooking.getStatus())
                .isEqualTo(BookingStatus.HELD);

        assertThat(persistedBooking.getConfirmedPaymentId()).isNull();

        assertThat(confirmedSeatRepository.count()).isZero();
    }

    @Test
    void confirmPaidBooking_shouldRollback_whenSeatWasAlreadySold() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        UUID firstPaymentId = UUID.randomUUID();
        UUID secondPaymentId = UUID.randomUUID();

        Booking firstBooking = persistHeldBooking(
                screeningId,
                seatId,
                now().plusMinutes(10));

        Booking secondBooking = persistHeldBooking(
                screeningId,
                seatId,
                now().plusMinutes(10));

        // the first booking acquires the temporary hold
        assertThat(redisSeatHoldService.holdSeat(
                screeningId,
                seatId,
                firstBooking.getId(),
                Duration.ofMinutes(10)
        )).isTrue();

        // the first payment confirms the booking and permanently sells the seat
        bookingConfirmationService.confirmPaidBooking(
                firstBooking.getId(),
                firstPaymentId);

        Booking confirmedFirstBooking = bookingRepository
                .findById(firstBooking.getId())
                .orElseThrow();

        assertThat(confirmedFirstBooking.getStatus())
                .isEqualTo(BookingStatus.CONFIRMED);

        assertThat(confirmedFirstBooking.getConfirmedPaymentId())
                .isEqualTo(firstPaymentId);

        // simulate the Redis hold being released after confirmation
        redisSeatHoldService.releaseSeat(screeningId, seatId, firstBooking.getId());

        // Redis may allow another temporary hold for the same seat
        assertThat(redisSeatHoldService.holdSeat(
                screeningId,
                seatId,
                secondBooking.getId(),
                Duration.ofMinutes(10)
        )).isTrue();

        // PostgreSQL must prevent selling the same seat again
        assertThatThrownBy(() -> bookingConfirmationService.confirmPaidBooking(
                secondBooking.getId(),
                secondPaymentId)
        ).isInstanceOf(DataIntegrityViolationException.class);

        Booking persistedSecondBooking = bookingRepository
                .findById(secondBooking.getId())
                .orElseThrow();

        // the failed transaction must preserve the previous booking state
        assertThat(persistedSecondBooking.getStatus()).isEqualTo(BookingStatus.HELD);

        assertThat(persistedSecondBooking.getConfirmedPaymentId()).isNull();

        // only the first booking may own the permanently sold seat
        assertThat(confirmedSeatRepository.count()).isEqualTo(1);

        Booking persistedFirstBooking = bookingRepository
                .findById(firstBooking.getId())
                .orElseThrow();

        assertThat(persistedFirstBooking.getConfirmedPaymentId()).isEqualTo(firstPaymentId);
    }

    private Booking persistHeldBooking(
            UUID screeningId,
            UUID seatId,
            LocalDateTime expiresAt
    ) {
        Booking booking = Booking.create(
                null,
                "guest@example.com",
                screeningId,
                now().minusMinutes(1));

        booking.addSeat(seatId, PRICE);
        booking.markAsHeld(expiresAt);

        return bookingRepository.saveAndFlush(booking);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
