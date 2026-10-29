package com.atlascinema.payment.provider;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/*
 * Defines the contract for charging and checking payments
 * Looking up a charge must not create a new charge
 */
public interface PaymentProvider {

    PaymentProviderResult charge(
            UUID paymentId,
            BigDecimal amount,
            String currencyCode);

    Optional<PaymentProviderResult> findChargeResult(UUID paymentId);
}
