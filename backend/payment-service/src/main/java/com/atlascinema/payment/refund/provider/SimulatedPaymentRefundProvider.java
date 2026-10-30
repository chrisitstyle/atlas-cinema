
package com.atlascinema.payment.refund.provider;

import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.SimulatedProviderCharge;
import com.atlascinema.payment.repository.SimulatedProviderTransactionRepository;
import com.atlascinema.payment.refund.repository.SimulatedProviderRefundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/*
 * Simulates idempotent refunds using persistent provider history
 * Rejects refunds for missing, declined or inconsistent charges
 */
@Component
@RequiredArgsConstructor
public class SimulatedPaymentRefundProvider implements PaymentRefundProvider {

    private final SimulatedProviderTransactionRepository chargeRepository;
    private final SimulatedProviderRefundRepository refundRepository;

    @Override
    public PaymentRefundResult refund(
            UUID paymentId,
            String providerTransactionId,
            BigDecimal amount,
            String currencyCode
    ) {
        Objects.requireNonNull(paymentId, "Payment id is required");
        Objects.requireNonNull(amount, "Refund amount is required");

        if (amount.signum() <= 0 || amount.scale() > 2) {
            throw new IllegalArgumentException("Invalid refund amount");
        }

        if (!"PLN".equals(currencyCode)) {
            throw new IllegalArgumentException("Unsupported refund currency");
        }

        SimulatedProviderCharge originalCharge = chargeRepository
                .findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalStateException("Cannot refund payment without an existing provider charge"));

        if (originalCharge.outcome() != PaymentProviderOutcome.APPROVED) {
            throw new IllegalStateException("Cannot refund a charge that was not approved");
        }

        if (!originalCharge.providerTransactionId().equals(providerTransactionId)
                || originalCharge.amount().compareTo(amount) != 0
                || !originalCharge.currencyCode().equals(currencyCode)) {
            throw new IllegalArgumentException("Refund details do not match the original provider charge");
        }

        SimulatedProviderRefund storedRefund = refundRepository.createOrGetRefund(
                        paymentId,
                        providerTransactionId,
                        amount,
                        currencyCode);

        // never accept different refund details for the same payment
        if (!storedRefund.providerTransactionId().equals(providerTransactionId)
                || storedRefund.amount().compareTo(amount) != 0
                || !storedRefund.currencyCode().equals(currencyCode)) {
            throw new IllegalArgumentException("Refund details differ from the original refund");
        }

        return storedRefund.toResult();
    }

    @Override
    public Optional<PaymentRefundResult> findRefundResult(UUID paymentId) {
        Objects.requireNonNull(paymentId, "Payment id is required");

        return refundRepository.findByPaymentId(paymentId)
                .map(SimulatedProviderRefund::toResult);
    }
}
