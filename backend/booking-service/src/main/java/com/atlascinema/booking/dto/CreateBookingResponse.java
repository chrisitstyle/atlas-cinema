package com.atlascinema.booking.dto;

import com.atlascinema.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Represents a newly created booking.
 * Includes a guest payment token only when the booking is anonymous.
 */
public record CreateBookingResponse(
        UUID id,
        UUID customerId,
        String contactEmail,
        UUID screeningId,
        BookingStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        BigDecimal totalPrice,
        List<BookingSeatResponse> seats,
        String guestPaymentToken
) {

    public static CreateBookingResponse from(
            BookingResponse booking,
            String guestPaymentToken) {
        return new CreateBookingResponse(
                booking.id(),
                booking.customerId(),
                booking.contactEmail(),
                booking.screeningId(),
                booking.status(),
                booking.createdAt(),
                booking.expiresAt(),
                booking.totalPrice(),
                booking.seats(),
                guestPaymentToken);
    }
}
