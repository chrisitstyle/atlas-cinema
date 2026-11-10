
package com.atlascinema.booking.domain;

import com.atlascinema.booking.exception.BookingCancellationNotAllowedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingGuestPaymentTokenTest {

    private static final String TOKEN_HASH = "a".repeat(64);

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    @BeforeEach
    void setUp() {
        createdAt = LocalDateTime.of(2030, Month.JANUARY, 10, 12, 0);
        expiresAt = createdAt.plusMinutes(10);
    }

    @Test
    void assignGuestPaymentTokenHash_shouldStoreHashForGuestBooking() {
        Booking booking = createGuestBooking();

        booking.assignGuestPaymentTokenHash(TOKEN_HASH);

        assertThat(booking.getGuestPaymentTokenHash()).isEqualTo(TOKEN_HASH);
    }

    @Test
    void assignGuestPaymentTokenHash_shouldRejectCustomerBooking() {
        Booking booking = Booking.create(
                UUID.randomUUID(),
                "customer@example.com",
                UUID.randomUUID(),
                createdAt);

        assertThatThrownBy(() ->
                booking.assignGuestPaymentTokenHash(TOKEN_HASH))
                .isInstanceOf(IllegalStateException.class);

        assertThat(booking.getGuestPaymentTokenHash()).isNull();
    }

    @Test
    void assignGuestPaymentTokenHash_shouldRejectInvalidHash() {
        Booking booking = createGuestBooking();

        assertThatThrownBy(() ->
                booking.assignGuestPaymentTokenHash("invalid-hash"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(booking.getGuestPaymentTokenHash()).isNull();
    }

    @Test
    void markAsHeld_shouldPreserveGuestPaymentTokenHash() {
        Booking booking = createGuestBookingWithToken();

        booking.markAsHeld(expiresAt);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);
        assertThat(booking.getGuestPaymentTokenHash())
                .isEqualTo(TOKEN_HASH);
    }

    @Test
    void cancel_shouldRevokeGuestPaymentToken() {
        Booking booking = createHeldGuestBooking();

        booking.cancel(
                createdAt.plusMinutes(2),
                createdAt.plusHours(2));

        assertThat(booking.getStatus())
                .isEqualTo(BookingStatus.CANCELLED);

        assertThat(booking.getGuestPaymentTokenHash()).isNull();
    }

    @Test
    void markAsExpired_shouldRevokeGuestPaymentToken() {
        Booking booking = createHeldGuestBooking();

        booking.markAsExpired();

        assertThat(booking.getStatus())
                .isEqualTo(BookingStatus.EXPIRED);

        assertThat(booking.getGuestPaymentTokenHash()).isNull();
    }

    @Test
    void markAsConfirmed_shouldRevokeGuestPaymentToken() {
        Booking booking = createHeldGuestBooking();

        UUID paymentId = UUID.randomUUID();

        booking.markAsConfirmed(
                createdAt.plusMinutes(2),
                paymentId);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        assertThat(booking.getConfirmedPaymentId()).isEqualTo(paymentId);

        assertThat(booking.getGuestPaymentTokenHash()).isNull();
    }

    @Test
    void cancelAfterPaymentFailure_shouldRevokeGuestPaymentToken() {
        Booking booking = createHeldGuestBooking();

        booking.cancelAfterPaymentFailure();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        assertThat(booking.getGuestPaymentTokenHash()).isNull();
    }

    @Test
    void cancel_shouldPreserveTokenWhenCancellationIsRejected() {
        Booking booking = createHeldGuestBooking();

        LocalDateTime screeningStartsAt = createdAt.plusHours(2);

        assertThatThrownBy(() -> booking.cancel(
                        screeningStartsAt,
                        screeningStartsAt)
        ).isInstanceOf(BookingCancellationNotAllowedException.class);

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.HELD);

        assertThat(booking.getGuestPaymentTokenHash()).isEqualTo(TOKEN_HASH);
    }

    private Booking createGuestBooking() {
        return Booking.create(
                null,
                "guest@example.com",
                UUID.randomUUID(),
                createdAt);
    }

    private Booking createGuestBookingWithToken() {
        Booking booking = createGuestBooking();

        booking.assignGuestPaymentTokenHash(TOKEN_HASH);

        return booking;
    }

    private Booking createHeldGuestBooking() {
        Booking booking = createGuestBookingWithToken();

        booking.addSeat(UUID.randomUUID(), new BigDecimal("29.90"));

        booking.markAsHeld(expiresAt);

        return booking;
    }
}
