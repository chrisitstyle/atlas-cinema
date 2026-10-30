package com.atlascinema.booking.event;

import java.util.UUID;

/*
 * Identifies the successful payment that requires compensation
 * Payment-service will validate the payment before issuing a refund
 */
public record PaymentRefundRequestedPayload(
        UUID paymentId,
        String providerTransactionId,
        String reason
) {
}
