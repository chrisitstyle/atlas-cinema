package com.atlascinema.payment.client.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
 * Represents booking data received from booking-service
 * Keeps payment-service independent from booking-service Java classes
 */
public record BookingPaymentDetailsResponse(
        UUID bookingId,
        UUID customerId,
        String status,
        Instant expiresAt,
        BigDecimal totalPrice,
        String currencyCode
) {
}
