
package com.atlascinema.payment.provider;

import com.atlascinema.payment.repository.SimulatedProviderTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/*
 * Simulates payment processing using a durable transaction history
 * Repeated charges return the original provider result
 */
@Component
public class SimulatedPaymentProvider implements PaymentProvider {

    private static final String SUPPORTED_CURRENCY_CODE = "PLN";

    private final SimulatedProviderTransactionRepository transactionRepository;
    private final PaymentProviderOutcome configuredOutcome;

    public SimulatedPaymentProvider(
            SimulatedProviderTransactionRepository transactionRepository,
            @Value("${payment.simulator.outcome:APPROVED}") String configuredOutcome
    ) {
        this.transactionRepository = transactionRepository;
        this.configuredOutcome =
                PaymentProviderOutcome.valueOf(configuredOutcome);
    }

    @Override
    public PaymentProviderResult charge(
            UUID paymentId,
            BigDecimal amount,
            String currencyCode
    ) {
        Objects.requireNonNull(paymentId, "Payment id is required");
        Objects.requireNonNull(amount, "Payment amount is required");

        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Payment amount must be positive and have at most two decimal places");
        }

        if (!SUPPORTED_CURRENCY_CODE.equals(currencyCode)) {
            throw new IllegalArgumentException("Only PLN payments are supported");
        }

        SimulatedProviderCharge storedCharge = transactionRepository.createOrGetCharge(
                        paymentId,
                        amount,
                        currencyCode,
                        configuredOutcome);

        // repeated charges must use the original amount and currency
        if (storedCharge.amount().compareTo(amount) != 0
                || !storedCharge.currencyCode().equals(currencyCode)) {
            throw new IllegalArgumentException("Payment charge details differ from the original charge");
        }

        return storedCharge.toProviderResult();
    }

    @Override
    public Optional<PaymentProviderResult> findChargeResult(UUID paymentId) {
        Objects.requireNonNull(paymentId, "Payment id is required");

        return transactionRepository.findByPaymentId(paymentId)
                .map(SimulatedProviderCharge::toProviderResult);
    }
}
