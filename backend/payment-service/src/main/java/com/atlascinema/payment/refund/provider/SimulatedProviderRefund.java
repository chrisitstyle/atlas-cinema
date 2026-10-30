package com.atlascinema.payment.refund.provider;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * Represents a refund stored in the simulated provider history
 */
public record SimulatedProviderRefund(
        UUID paymentId,
        String providerTransactionId,
        BigDecimal amount,
        String currencyCode,
        String providerRefundTransactionId
) {
    public PaymentRefundResult toResult() {
        return new PaymentRefundResult(providerRefundTransactionId);
    }
}