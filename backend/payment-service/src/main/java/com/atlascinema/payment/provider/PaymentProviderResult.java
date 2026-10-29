package com.atlascinema.payment.provider;

/*
 * Contains the payment provider's definitive processing result
 * The transaction identifier allows later payment reconciliation
 */
public record PaymentProviderResult(
        PaymentProviderOutcome outcome,
        String providerTransactionId
) {
}
