package com.atlascinema.booking.domain;

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

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        screeningId = UUID.randomUUID();

        createdAt = LocalDateTime.of(2030, 1, 10, 12, 0);
    }

    @Test
    void create_shouldCreatePendingBooking_whenCustomerIsLoggedIn() {
        Booking booking = Booking.create(
                customerId,
                "john@example.com",
                screeningId,
                createdAt);

        assertThat(booking.getId()).isNotNull();

        assertThat(booking.getCustomerId())
                .isEqualTo(customerId);

        assertThat(booking.getContactEmail())
                .isEqualTo("john@example.com");

        assertThat(booking.getScreeningId())
                .isEqualTo(screeningId);

        assertThat(booking.getStatus())
                .isEqualTo(BookingStatus.PENDING);

        assertThat(booking.getCreatedAt())
                .isEqualTo(createdAt);

        assertThat(booking.getExpiresAt())
                .isNull();

        assertThat(booking.getTotalPrice())
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(booking.getSeats())
                .isEmpty();
    }

    @Test
    void create_shouldCreatePendingBooking_whenCustomerIsGuest() {
        Booking booking = Booking.create(
                null,
                "guest@example.com",
                screeningId,
                createdAt);

        assertThat(booking.getCustomerId())
                .isNull();

        assertThat(booking.getContactEmail())
                .isEqualTo("guest@example.com");

        assertThat(booking.getStatus())
                .isEqualTo(BookingStatus.PENDING);
    }

    @Test
    void addSeat_shouldAddSeat_whenSeatIsNotAlreadyAdded() {
        Booking booking = createBooking();

        UUID seatId = UUID.randomUUID();

        booking.addSeat(seatId,
                new BigDecimal("29.90"));

        assertThat(booking.getSeats())
                .hasSize(1);

        BookingSeat bookingSeat = booking.getSeats().getFirst();

        assertThat(bookingSeat.getSeatId())
                .isEqualTo(seatId);

        assertThat(bookingSeat.getScreeningId())
                .isEqualTo(screeningId);

        assertThat(bookingSeat.getBooking())
                .isSameAs(booking);

        assertThat(bookingSeat.getPrice())
                .isEqualByComparingTo("29.90");
    }

    @Test
    void addSeat_shouldIncreaseTotalPrice_whenSeatIsAdded() {
        Booking booking = createBooking();

        booking.addSeat(UUID.randomUUID(),
                new BigDecimal("29.90"));

        assertThat(booking.getTotalPrice())
                .isEqualByComparingTo("29.90");
    }

    @Test
    void addSeat_shouldCalculateTotalPrice_whenMultipleSeatsAreAdded() {
        Booking booking = createBooking();

        booking.addSeat(UUID.randomUUID(),
                new BigDecimal("29.90"));

        booking.addSeat(UUID.randomUUID(),
                new BigDecimal("39.90"));

        booking.addSeat(UUID.randomUUID(),
                new BigDecimal("49.90"));

        assertThat(booking.getSeats())
                .hasSize(3);

        assertThat(booking.getTotalPrice())
                .isEqualByComparingTo("119.70");
    }

    @Test
    void addSeat_shouldThrowSeatAlreadyAddedException_whenSeatIsAlreadyAdded() {
        Booking booking = createBooking();

        UUID seatId = UUID.randomUUID();

        booking.addSeat(seatId,
                new BigDecimal("29.90"));

        assertThatThrownBy(() -> booking.addSeat(
                        seatId, new BigDecimal("29.90")))
                .isInstanceOf(SeatAlreadyAddedException.class)
                .hasMessageContaining(seatId.toString());

        assertThat(booking.getSeats())
                .hasSize(1);

        assertThat(booking.getTotalPrice())
                .isEqualByComparingTo("29.90");
    }

    private Booking createBooking() {
        return Booking.create(
                customerId,
                "john@example.com",
                screeningId,
                createdAt);
    }
}
