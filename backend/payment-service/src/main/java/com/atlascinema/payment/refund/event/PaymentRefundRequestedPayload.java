package com.atlascinema.payment.refund.event;

import java.util.UUID;

/*
 * Contains the payment details required to start compensation
 */
public record PaymentRefundRequestedPayload(
        UUID paymentId,
        String providerTransactionId,
        String reason
) {
}