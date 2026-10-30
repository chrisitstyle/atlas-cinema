package com.atlascinema.payment.refund.service.dto;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * Contains immutable details required to issue a provider refund
 */
public record PaymentRefundDetails(
        UUID paymentId,
        String providerTransactionId,
        BigDecimal amount,
        String currencyCode
) {
}