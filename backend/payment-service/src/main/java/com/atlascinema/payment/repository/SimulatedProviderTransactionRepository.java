
package com.atlascinema.payment.repository;

import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.SimulatedProviderCharge;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/*
 * Stores charge results for the simulated payment provider
 * A unique payment id prevents repeated charges from changing the result
 */
@Repository
@RequiredArgsConstructor
public class SimulatedProviderTransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    /*
     * Commits the simulated provider result in its own database transaction
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SimulatedProviderCharge createOrGetCharge(
            UUID paymentId,
            BigDecimal amount,
            String currencyCode,
            PaymentProviderOutcome requestedOutcome
    ) {
        String providerTransactionId = "SIM-" + paymentId;

        jdbcTemplate.update("""
                INSERT INTO simulated_provider_transactions (
                    payment_id,
                    amount,
                    currency_code,
                    outcome,
                    provider_transaction_id
                )
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (payment_id) DO NOTHING
                """,
                paymentId,
                amount,
                currencyCode,
                requestedOutcome.name(),
                providerTransactionId);

        SimulatedProviderCharge storedCharge = findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalStateException("Simulated charge was not found after insertion"));


        return storedCharge;
    }

    public Optional<SimulatedProviderCharge> findByPaymentId(UUID paymentId) {
        return jdbcTemplate.query("""
                        SELECT
                            payment_id,
                            amount,
                            currency_code,
                            outcome,
                            provider_transaction_id
                        FROM simulated_provider_transactions
                        WHERE payment_id = ?
                        """,
                (resultSet, rowNumber) -> new SimulatedProviderCharge(
                        resultSet.getObject("payment_id", UUID.class),
                        resultSet.getBigDecimal("amount"),
                        resultSet.getString("currency_code"),
                        PaymentProviderOutcome.valueOf(
                                resultSet.getString("outcome")
                        ),
                        resultSet.getString("provider_transaction_id")
                ),
                paymentId).stream().findFirst();
    }
}
