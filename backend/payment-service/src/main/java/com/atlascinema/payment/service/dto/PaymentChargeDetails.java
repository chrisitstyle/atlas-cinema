package com.atlascinema.payment.service.dto;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * Contains immutable payment details required by the payment provider
 */
public record PaymentChargeDetails(
        UUID paymentId,
        BigDecimal amount,
        String currencyCode
) {
}
