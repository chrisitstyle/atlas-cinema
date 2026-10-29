package com.atlascinema.payment.provider;

import com.atlascinema.payment.repository.SimulatedProviderTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Verifies the simulated provider contract without starting Spring
 */
@ExtendWith(MockitoExtension.class)
class SimulatedPaymentProviderTest {

    private static final UUID PAYMENT_ID = UUID.fromString("c4acba5f-dc51-4e8a-b870-88b768da370e");

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("59.80");

    private static final String CURRENCY_CODE = "PLN";

    @Mock
    private SimulatedProviderTransactionRepository transactionRepository;

    @Test
    void charge_shouldReturnApproved_whenStoredOutcomeIsApproved() {
        SimulatedPaymentProvider paymentProvider = new SimulatedPaymentProvider(
                        transactionRepository,
                        "APPROVED");

        when(transactionRepository.createOrGetCharge(
                PAYMENT_ID,
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                PaymentProviderOutcome.APPROVED
        )).thenReturn(createStoredCharge(PaymentProviderOutcome.APPROVED));

        PaymentProviderResult result = paymentProvider.charge(
                PAYMENT_ID,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        assertThat(result.outcome()).isEqualTo(PaymentProviderOutcome.APPROVED);

        assertThat(result.providerTransactionId()).isEqualTo("SIM-" + PAYMENT_ID);
    }

    @Test
    void charge_shouldReturnDeclined_whenStoredOutcomeIsDeclined() {
        SimulatedPaymentProvider paymentProvider = new SimulatedPaymentProvider(
                        transactionRepository,
                        "DECLINED");

        when(transactionRepository.createOrGetCharge(
                PAYMENT_ID,
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                PaymentProviderOutcome.DECLINED
        )).thenReturn(createStoredCharge(PaymentProviderOutcome.DECLINED));

        PaymentProviderResult result = paymentProvider.charge(
                PAYMENT_ID,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        assertThat(result.outcome()).isEqualTo(PaymentProviderOutcome.DECLINED);
    }

    @Test
    void charge_shouldReturnOriginalOutcome_whenConfiguredOutcomeChanges() {
        SimulatedPaymentProvider paymentProvider = new SimulatedPaymentProvider(
                        transactionRepository,
                        "DECLINED");

        when(transactionRepository.createOrGetCharge(
                PAYMENT_ID,
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                PaymentProviderOutcome.DECLINED
        )).thenReturn(createStoredCharge(PaymentProviderOutcome.APPROVED));

        PaymentProviderResult result = paymentProvider.charge(
                PAYMENT_ID,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        assertThat(result.outcome()).isEqualTo(PaymentProviderOutcome.APPROVED);
    }

    @Test
    void findChargeResult_shouldReturnStoredResult_whenChargeExists() {
        SimulatedPaymentProvider paymentProvider = new SimulatedPaymentProvider(
                        transactionRepository,
                        "APPROVED");

        when(transactionRepository.findByPaymentId(PAYMENT_ID))
                .thenReturn(Optional.of(
                        createStoredCharge(PaymentProviderOutcome.APPROVED)));

        Optional<PaymentProviderResult> result = paymentProvider.findChargeResult(PAYMENT_ID);

        assertThat(result).isPresent();

        assertThat(result.orElseThrow().outcome()).isEqualTo(PaymentProviderOutcome.APPROVED);

        verify(transactionRepository).findByPaymentId(PAYMENT_ID);
    }

    @Test
    void findChargeResult_shouldReturnEmpty_whenChargeDoesNotExist() {
        SimulatedPaymentProvider paymentProvider = new SimulatedPaymentProvider(
                        transactionRepository,
                        "APPROVED");

        when(transactionRepository.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());

        assertThat(paymentProvider.findChargeResult(PAYMENT_ID)).isEmpty();
    }

    @Test
    void charge_shouldRejectRequest_whenAmountIsNotPositive() {
        SimulatedPaymentProvider paymentProvider = new SimulatedPaymentProvider(
                        transactionRepository,
                        "APPROVED");

        assertThatThrownBy(() -> paymentProvider.charge(
                PAYMENT_ID,
                BigDecimal.ZERO,
                CURRENCY_CODE
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void charge_shouldRejectRequest_whenCurrencyIsUnsupported() {
        SimulatedPaymentProvider paymentProvider = new SimulatedPaymentProvider(
                        transactionRepository,
                        "APPROVED");

        assertThatThrownBy(() -> paymentProvider.charge(
                PAYMENT_ID,
                PAYMENT_AMOUNT,
                "EUR"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private SimulatedProviderCharge createStoredCharge(
            PaymentProviderOutcome outcome
    ) {
        return new SimulatedProviderCharge(
                PAYMENT_ID,
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                outcome,
                "SIM-" + PAYMENT_ID);
    }
}
