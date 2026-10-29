package com.atlascinema.payment.provider;

import java.math.BigDecimal;
import java.util.UUID;

/*
 * Represents a charge stored by the simulated payment provider
 */
public record SimulatedProviderCharge(
        UUID paymentId,
        BigDecimal amount,
        String currencyCode,
        PaymentProviderOutcome outcome,
        String providerTransactionId
) {

    public PaymentProviderResult toProviderResult() {
        return new PaymentProviderResult(outcome, providerTransactionId);
    }
}