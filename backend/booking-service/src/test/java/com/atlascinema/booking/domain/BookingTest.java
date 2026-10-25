package com.atlascinema.booking.domain;

import com.atlascinema.booking.exception.BookingCancellationNotAllowedException;
import com.atlascinema.booking.exception.SeatAlreadyAddedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingTest {

    private UUID customerId;
    private UUID screeningId;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        screeningId = UUID.randomUUID();

        createdAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        expiresAt = createdAt.plusMinutes(10);
    }

    @Test
    void create_shouldCreatePendingBooking_whenCustomerIsLoggedIn() {
        Booking booking = Booking.create(
                customerId,
                "john@example.com",
                screeningId,
                createdAt);

        assertThat(booking.getId()).isNotNull();

        assertThat(booking.getCustomerId()).isEqualTo(customerId);

        assertThat(booking.getContactEmail()).isEqualTo("john@example.com");

        assertThat(booking.getScreeningId()).isEqualTo(screeningId);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);

        assertThat(booking.getCreatedAt()).isEqualTo(createdAt);

        assertThat(booking.getExpiresAt()).isNull();

        assertThat(booking.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(booking.getSeats()).isEmpty();

        assertThat(booking.getHolds()).isEmpty();
    }

    @Test
    void create_shouldCreatePendingBooking_whenCustomerIsGuest() {
        Booking booking = Booking.create(
                null,
                "guest@example.com",
                screeningId,
                createdAt);

        assertThat(booking.getCustomerId()).isNull();

        assertThat(booking.getContactEmail()).isEqualTo("guest@example.com");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void holdSeat_shouldAddBookingSeatAndSeatHold_whenSeatIsNotAlreadyAdded() {
        Booking booking = createBooking();

        UUID seatId = UUID.randomUUID();

        booking.holdSeat(
                seatId,
                new BigDecimal("29.90"),
                expiresAt);

        assertThat(booking.getSeats()).hasSize(1);

        BookingSeat bookingSeat = booking.getSeats().getFirst();

        assertThat(bookingSeat.getSeatId()).isEqualTo(seatId);

        assertThat(bookingSeat.getPrice()).isEqualByComparingTo("29.90");

        assertThat(bookingSeat.getBooking()).isSameAs(booking);

        assertThat(booking.getHolds()).hasSize(1);

        SeatHold hold = booking.getHolds().getFirst();

        assertThat(hold.getSeatId()).isEqualTo(seatId);

        assertThat(hold.getScreeningId()).isEqualTo(screeningId);

        assertThat(hold.getExpiresAt()).isEqualTo(expiresAt);

        assertThat(hold.getBooking()).isSameAs(booking);
    }

    @Test
    void holdSeat_shouldIncreaseTotalPrice_whenSeatIsAdded() {
        Booking booking = createBooking();

        booking.holdSeat(
                UUID.randomUUID(),
                new BigDecimal("29.90"),
                expiresAt);

        assertThat(booking.getTotalPrice()).isEqualByComparingTo("29.90");
    }

    @Test
    void holdSeat_shouldCalculateTotalPrice_whenMultipleSeatsAreAdded() {
        Booking booking = createBooking();

        booking.holdSeat(
                UUID.randomUUID(),
                new BigDecimal("29.90"),
                expiresAt);

        booking.holdSeat(
                UUID.randomUUID(),
                new BigDecimal("39.90"),
                expiresAt);

        booking.holdSeat(
                UUID.randomUUID(),
                new BigDecimal("49.90"),
                expiresAt);

        assertThat(booking.getSeats()).hasSize(3);

        assertThat(booking.getHolds()).hasSize(3);

        assertThat(booking.getTotalPrice()).isEqualByComparingTo("119.70");
    }

    @Test
    void holdSeat_shouldThrowSeatAlreadyAddedException_whenSeatIsAlreadyAdded() {
        Booking booking = createBooking();

        UUID seatId = UUID.randomUUID();

        booking.holdSeat(
                seatId,
                new BigDecimal("29.90"),
                expiresAt);

        assertThatThrownBy(() -> booking.holdSeat(
                        seatId,
                        new BigDecimal("29.90"),
                        expiresAt)
        )
                .isInstanceOf(SeatAlreadyAddedException.class)
                .hasMessageContaining(seatId.toString());

        assertThat(booking.getSeats()).hasSize(1);

        assertThat(booking.getHolds()).hasSize(1);

        assertThat(booking.getTotalPrice()).isEqualByComparingTo("29.90");
    }

    @Test
    void markAsHeld_shouldChangeStatusAndExpiresAt_whenSeatsAreHeld() {
        Booking booking = createBooking();

        booking.holdSeat(
                UUID.randomUUID(),
                new BigDecimal("29.90"),
                expiresAt);

        booking.markAsHeld(expiresAt);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);

        assertThat(booking.getExpiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void cancel_shouldCancelBookingAndReleaseHolds_whenBookingIsHeldAndScreeningHasNotStarted() {
        Booking booking = createBooking();

        UUID seatId = UUID.randomUUID();

        booking.holdSeat(
                seatId,
                new BigDecimal("29.90"),
                expiresAt);

        booking.markAsHeld(expiresAt);

        LocalDateTime now = createdAt.plusMinutes(5);

        LocalDateTime screeningStartsAt = createdAt.plusHours(2);

        booking.cancel(now, screeningStartsAt);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        assertThat(booking.getHolds()).isEmpty();

        assertThat(booking.getSeats()).hasSize(1);

        assertThat(booking.getSeats().getFirst().getSeatId()).isEqualTo(seatId);
    }

    @Test
    void cancel_shouldThrowBookingCancellationNotAllowedException_whenScreeningHasAlreadyStarted() {
        Booking booking = createBooking();

        booking.holdSeat(UUID.randomUUID(),
                new BigDecimal("29.90"),
                expiresAt);

        booking.markAsHeld(expiresAt);

        LocalDateTime screeningStartsAt = createdAt.plusHours(2);

        LocalDateTime now = screeningStartsAt.plusMinutes(1);

        assertThatThrownBy(() -> booking.cancel(now, screeningStartsAt))
                .isInstanceOf(BookingCancellationNotAllowedException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);

        assertThat(booking.getHolds()).hasSize(1);
    }

    @Test
    void cancel_shouldThrowBookingCancellationNotAllowedException_whenScreeningStartsNow() {
        Booking booking = createBooking();

        booking.holdSeat(
                UUID.randomUUID(),
                new BigDecimal("29.90"),
                expiresAt
        );

        booking.markAsHeld(expiresAt);

        LocalDateTime screeningStartsAt = createdAt.plusHours(2);

        assertThatThrownBy(() -> booking.cancel(screeningStartsAt, screeningStartsAt))
                .isInstanceOf(BookingCancellationNotAllowedException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);

        assertThat(booking.getHolds())
                .hasSize(1);
    }

    @Test
    void cancel_shouldThrowBookingCancellationNotAllowedException_whenBookingIsNotHeld() {
        Booking booking = createBooking();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);

        assertThatThrownBy(() -> booking.cancel(createdAt, createdAt.plusHours(2)))
                .isInstanceOf(BookingCancellationNotAllowedException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    private Booking createBooking() {
        return Booking.create(
                customerId,
                "john@example.com",
                screeningId,
                createdAt);
    }
}