package com.atlascinema.payment.client.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/*
 * Contains the booking identifier for a new payment initiation request
 * Payment amount and currency are retrieved from booking-service
 */
public record InitiatePaymentRequest(

        @NotNull
        UUID bookingId
) {
}
