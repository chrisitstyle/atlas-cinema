package com.atlascinema.payment.provider;

/*
 * Represents a definitive response from a payment provider
 * Communication failures do not imply a declined payment
 */
public enum PaymentProviderOutcome {

    APPROVED,
    DECLINED
}
