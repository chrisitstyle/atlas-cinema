package com.atlascinema.booking.dto;

import com.atlascinema.booking.domain.BookingSeat;

import java.math.BigDecimal;
import java.util.UUID;

public record BookingSeatResponse(
        UUID id,
        UUID seatId,
        BigDecimal price
) {

    public static BookingSeatResponse from(BookingSeat bookingSeat) {
        return new BookingSeatResponse(
                bookingSeat.getId(),
                bookingSeat.getSeatId(),
                bookingSeat.getPrice());
    }
}
