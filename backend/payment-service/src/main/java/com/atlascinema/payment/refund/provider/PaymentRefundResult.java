package com.atlascinema.payment.refund.provider;

/*
 * Represents a refund confirmed by the payment provider
 */
public record PaymentRefundResult(
        String providerRefundTransactionId
) {
    public PaymentRefundResult {
        if (providerRefundTransactionId == null
                || providerRefundTransactionId.isBlank()
                || providerRefundTransactionId.length() > 128) {
            throw new IllegalArgumentException("Invalid provider refund transaction id");
        }
    }
}