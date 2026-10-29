package com.atlascinema.payment.client.dto;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/*
 * Exposes payment details without returning the JPA entity directly
 */
public record PaymentResponse(
        UUID paymentId,
        UUID bookingId,
        BigDecimal amount,
        String currencyCode,
        PaymentStatus status,
        Instant createdAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                payment.getAmount(),
                payment.getCurrencyCode(),
                payment.getStatus(),
                payment.getCreatedAt());
    }
}
