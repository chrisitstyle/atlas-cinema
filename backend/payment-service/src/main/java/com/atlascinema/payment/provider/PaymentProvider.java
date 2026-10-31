package com.atlascinema.payment.provider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/*
 * Defines payment charging and result lookup operations
 * Charge retries must be idempotent for the same payment id
 */
public interface PaymentProvider {

    /*
     * Repeated calls for the same payment id must not charge twice
     * a provider adapter must use a stable external idempotency key
     */
    PaymentProviderResult charge(
            UUID paymentId,
            BigDecimal amount,
            String currencyCode
    );

    /*
     * Looks up an earlier charge without creating a new transaction
     * an empty result does not prove that the charge never happened
     */
    Optional<PaymentProviderResult> findChargeResult(UUID paymentId);
}