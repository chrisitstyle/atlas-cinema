package com.atlascinema.payment.provider;


import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.repository.SimulatedProviderTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies durable and idempotent simulated charges using PostgreSQL
 */
class SimulatedPaymentProviderIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("59.80");

    private static final String CURRENCY_CODE = "PLN";

    @Autowired
    private SimulatedProviderTransactionRepository transactionRepository;

    @Test
    void charge_shouldPersistProviderResult_whenChargeIsNew() {
        UUID paymentId = UUID.randomUUID();

        SimulatedPaymentProvider paymentProvider = createPaymentProvider("APPROVED");

        PaymentProviderResult result = paymentProvider.charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        SimulatedProviderCharge storedCharge = transactionRepository
                .findByPaymentId(paymentId)
                .orElseThrow();

        assertThat(result.outcome()).isEqualTo(PaymentProviderOutcome.APPROVED);

        assertThat(storedCharge.paymentId()).isEqualTo(paymentId);

        assertThat(storedCharge.amount()).isEqualByComparingTo(PAYMENT_AMOUNT);

        assertThat(storedCharge.currencyCode()).isEqualTo(CURRENCY_CODE);

        assertThat(storedCharge.outcome()).isEqualTo(PaymentProviderOutcome.APPROVED);

        assertThat(storedCharge.providerTransactionId()).isEqualTo("SIM-" + paymentId);
    }

    @Test
    void charge_shouldReuseOriginalResult_whenProviderConfigurationChanges() {
        UUID paymentId = UUID.randomUUID();

        SimulatedPaymentProvider approvingProvider = createPaymentProvider("APPROVED");

        SimulatedPaymentProvider decliningProvider = createPaymentProvider("DECLINED");

        PaymentProviderResult originalResult = approvingProvider.charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        PaymentProviderResult repeatedResult = decliningProvider.charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        assertThat(originalResult.outcome()).isEqualTo(PaymentProviderOutcome.APPROVED);

        assertThat(repeatedResult).isEqualTo(originalResult);
    }

    @Test
    void findChargeResult_shouldReadPersistedResult_withoutChargingAgain() {
        UUID paymentId = UUID.randomUUID();

        SimulatedPaymentProvider paymentProvider = createPaymentProvider("DECLINED");

        PaymentProviderResult originalResult = paymentProvider.charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        SimulatedPaymentProvider newProviderInstance =
                createPaymentProvider("APPROVED");

        Optional<PaymentProviderResult> recoveredResult = newProviderInstance.findChargeResult(paymentId);

        assertThat(recoveredResult).contains(originalResult);

        assertThat(recoveredResult.orElseThrow().outcome()).isEqualTo(PaymentProviderOutcome.DECLINED);
    }

    @Test
    void findChargeResult_shouldReturnEmpty_whenChargeWasNeverCreated() {
        SimulatedPaymentProvider paymentProvider = createPaymentProvider("APPROVED");

        assertThat(paymentProvider.findChargeResult(UUID.randomUUID())).isEmpty();
    }

    @Test
    void charge_shouldRejectRepeatedRequest_whenAmountDiffers() {
        UUID paymentId = UUID.randomUUID();

        SimulatedPaymentProvider paymentProvider = createPaymentProvider("APPROVED");

        paymentProvider.charge(paymentId, PAYMENT_AMOUNT, CURRENCY_CODE);

        assertThatThrownBy(() -> paymentProvider.charge(
                paymentId,
                new BigDecimal("99.90"),
                CURRENCY_CODE
        )).isInstanceOf(IllegalArgumentException.class);

        SimulatedProviderCharge storedCharge = transactionRepository
                .findByPaymentId(paymentId)
                .orElseThrow();

        assertThat(storedCharge.amount()).isEqualByComparingTo(PAYMENT_AMOUNT);
    }

    private SimulatedPaymentProvider createPaymentProvider(
            String configuredOutcome
    ) {
        return new SimulatedPaymentProvider(transactionRepository, configuredOutcome);
    }
}
