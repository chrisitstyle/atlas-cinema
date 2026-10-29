package com.atlascinema.booking.dto;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/*
 * Exposes only the booking data required to initiate a payment
 * Converts the local expiration time into an absolute instant
 */
public record BookingPaymentDetailsResponse(
        UUID bookingId,
        UUID customerId,
        BookingStatus status,
        Instant expiresAt,
        BigDecimal totalPrice,
        String currencyCode
) {

    private static final String SUPPORTED_CURRENCY_CODE = "PLN";

    public static BookingPaymentDetailsResponse from(
            Booking booking,
            ZoneId bookingTimeZone) {
        Instant expirationInstant = booking.getExpiresAt() == null
                ? null
                : booking.getExpiresAt()
                .atZone(bookingTimeZone)
                .toInstant();

        return new BookingPaymentDetailsResponse(
                booking.getId(),
                booking.getCustomerId(),
                booking.getStatus(),
                expirationInstant,
                booking.getTotalPrice(),
                SUPPORTED_CURRENCY_CODE);
    }
}
