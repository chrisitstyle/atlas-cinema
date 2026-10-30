
package com.atlascinema.payment.refund.repository;

import com.atlascinema.payment.refund.provider.SimulatedProviderRefund;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/*
 * Stores refunds independently from the main payment transaction
 * Each payment can have at most one simulated provider refund
 */
@Repository
@RequiredArgsConstructor
public class SimulatedProviderRefundRepository {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SimulatedProviderRefund createOrGetRefund(
            UUID paymentId,
            String providerTransactionId,
            BigDecimal amount,
            String currencyCode
    ) {
        String providerRefundTransactionId = "SIM-REF-" + paymentId;

        jdbcTemplate.update("""
                        INSERT INTO simulated_provider_refunds (
                            payment_id,
                            provider_transaction_id,
                            amount,
                            currency_code,
                            provider_refund_transaction_id
                        )
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (payment_id) DO NOTHING
                        """,
                paymentId,
                providerTransactionId,
                amount,
                currencyCode,
                providerRefundTransactionId);

        return findByPaymentId(paymentId)
                .orElseThrow(() -> new IllegalStateException("Simulated refund was not found after insertion"));
    }

    public Optional<SimulatedProviderRefund> findByPaymentId(UUID paymentId) {
        return jdbcTemplate.query("""
                        SELECT
                            payment_id,
                            provider_transaction_id,
                            amount,
                            currency_code,
                            provider_refund_transaction_id
                        FROM simulated_provider_refunds
                        WHERE payment_id = ?
                        """,
                (resultSet, rowNumber) -> new SimulatedProviderRefund(
                        resultSet.getObject("payment_id", UUID.class),
                        resultSet.getString("provider_transaction_id"),
                        resultSet.getBigDecimal("amount"),
                        resultSet.getString("currency_code"),
                        resultSet.getString("provider_refund_transaction_id")
                ),
                paymentId).stream().findFirst();
    }
}
