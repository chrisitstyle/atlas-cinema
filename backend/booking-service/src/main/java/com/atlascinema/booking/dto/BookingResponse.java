package com.atlascinema.booking.dto;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID customerId,
        String contactEmail,
        UUID screeningId,
        BookingStatus status,
        LocalDateTime createdAt,
        LocalDateTime expiresAt,
        BigDecimal totalPrice,
        List<BookingSeatResponse> seats
) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getCustomerId(),
                booking.getContactEmail(),
                booking.getScreeningId(),
                booking.getStatus(),
                booking.getCreatedAt(),
                booking.getExpiresAt(),
                booking.getTotalPrice(),
                booking.getSeats()
                        .stream()
                        .map(BookingSeatResponse::from)
                        .toList());
    }
}
