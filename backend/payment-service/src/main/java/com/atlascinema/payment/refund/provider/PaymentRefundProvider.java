package com.atlascinema.payment.refund.provider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/*
 * Defines idempotent refund operations supported by a payment provider
 * The payment id is the stable refund idempotency key
 */
public interface PaymentRefundProvider {

    PaymentRefundResult refund(
            UUID paymentId,
            String providerTransactionId,
            BigDecimal amount,
            String currencyCode);

    // reading a refund result must never create another refund
    Optional<PaymentRefundResult> findRefundResult(UUID paymentId);
}