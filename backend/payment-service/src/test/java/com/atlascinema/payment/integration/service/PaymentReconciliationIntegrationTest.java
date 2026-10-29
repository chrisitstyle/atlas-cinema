
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.PaymentNotFoundException;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.provider.SimulatedPaymentProvider;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.repository.SimulatedProviderTransactionRepository;
import com.atlascinema.payment.service.PaymentProcessingTransactionService;
import com.atlascinema.payment.service.PaymentReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies recovery of interrupted payments using real PostgreSQL
 * Provider results are stored separately from the payment status
 */
class PaymentReconciliationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    private static final Instant INITIATED_AT = Instant.parse("2026-10-27T10:00:00Z");

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SimulatedProviderTransactionRepository simulatedProviderTransactionRepository;

    @Autowired
    private PaymentProcessingTransactionService paymentProcessingTransactionService;

    @Autowired
    private PaymentReconciliationService paymentReconciliationService;

    @Test
    void reconcilePayment_shouldRecoverSucceededPayment_whenProviderApprovedCharge() {
        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        // simulate a successful provider charge without updating the payment
        createSimulatedProvider("APPROVED").charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        Payment paymentBeforeRecovery = paymentRepository.findById(paymentId).orElseThrow();

        assertThat(paymentBeforeRecovery.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        assertThat(paymentBeforeRecovery.getProviderTransactionId()).isNull();

        Payment recoveredPayment = paymentReconciliationService.reconcilePayment(paymentId);

        Payment persistedPayment = paymentRepository.findById(paymentId).orElseThrow();

        assertThat(recoveredPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getProviderTransactionId()).isEqualTo("SIM-" + paymentId);

        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void reconcilePayment_shouldRecoverFailedPayment_whenProviderDeclinedCharge() {
        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        // the provider has declined the payment but our database still shows PROCESSING
        createSimulatedProvider("DECLINED").charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        Payment recoveredPayment = paymentReconciliationService.reconcilePayment(paymentId);

        Payment persistedPayment = paymentRepository.findById(paymentId).orElseThrow();

        assertThat(recoveredPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(persistedPayment.getProviderTransactionId()).isEqualTo("SIM-" + paymentId);
    }

    @Test
    void reconcilePayment_shouldKeepProcessingStatus_whenProviderResultIsMissing() {
        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        // no charge is stored by the simulated provider
        Payment reconciledPayment = paymentReconciliationService.reconcilePayment(paymentId);

        Payment persistedPayment = paymentRepository.findById(paymentId).orElseThrow();

        assertThat(reconciledPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        assertThat(persistedPayment.getProviderTransactionId()).isNull();

        assertThat(simulatedProviderTransactionRepository.findByPaymentId(paymentId)).isEmpty();
    }


    @Test
    void reconcilePayment_shouldReuseCompletedPayment_whenCalledAgain() {
        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        createSimulatedProvider("APPROVED").charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        Payment firstRecovery = paymentReconciliationService.reconcilePayment(paymentId);

        // capture the timestamp exactly as PostgreSQL stored it
        Payment persistedPaymentAfterFirstRecovery = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        Instant originalPersistedUpdatedAt = persistedPaymentAfterFirstRecovery.getUpdatedAt();

        Payment repeatedRecovery = paymentReconciliationService.reconcilePayment(paymentId);

        Payment persistedPaymentAfterRepeatedRecovery = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(firstRecovery.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(repeatedRecovery.getId()).isEqualTo(firstRecovery.getId());

        assertThat(repeatedRecovery.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        // reconciliation must not change an already completed payment
        assertThat(persistedPaymentAfterRepeatedRecovery.getUpdatedAt()).isEqualTo(originalPersistedUpdatedAt);

        assertThat(repeatedRecovery.getUpdatedAt()).isEqualTo(originalPersistedUpdatedAt);

        assertThat(paymentRepository.count()).isEqualTo(1);
    }


    @Test
    void reconcilePayment_shouldLeavePendingPaymentUnchanged() {
        Payment pendingPayment = persistPendingPayment();

        Payment reconciledPayment = paymentReconciliationService.reconcilePayment(
                pendingPayment.getId());

        assertThat(reconciledPayment.getStatus())
                .isEqualTo(PaymentStatus.PENDING);

        assertThat(simulatedProviderTransactionRepository
                .findByPaymentId(pendingPayment.getId()))
                .isEmpty();
    }

    @Test
    void reconcilePayment_shouldRejectRequest_whenPaymentDoesNotExist() {
        UUID nonexistentPaymentId = UUID.randomUUID();

        assertThatThrownBy(() -> paymentReconciliationService.reconcilePayment(nonexistentPaymentId)
        ).isInstanceOf(PaymentNotFoundException.class);
    }


    @Test
    void completeProcessing_shouldReuseIdenticalResult_whenCalledTwice() {
        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        PaymentProviderResult providerResult = new PaymentProviderResult(
                PaymentProviderOutcome.APPROVED,
                "SIM-" + paymentId);

        Payment firstCompletion = paymentProcessingTransactionService.completeProcessing(
                        paymentId,
                        providerResult);

        // read the timestamp stored by PostgreSQL instead of using in-memory precision
        Payment persistedPaymentAfterFirstCompletion = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        Instant originalPersistedUpdatedAt = persistedPaymentAfterFirstCompletion.getUpdatedAt();

        Payment repeatedCompletion = paymentProcessingTransactionService.completeProcessing(
                        paymentId,
                        providerResult);

        Payment persistedPaymentAfterRepeatedCompletion = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(firstCompletion.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(repeatedCompletion.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(repeatedCompletion.getProviderTransactionId()).isEqualTo("SIM-" + paymentId);

        // repeating the same result must not update the stored timestamp
        assertThat(persistedPaymentAfterRepeatedCompletion.getUpdatedAt())
                .isEqualTo(originalPersistedUpdatedAt);

        assertThat(repeatedCompletion.getUpdatedAt())
                .isEqualTo(originalPersistedUpdatedAt);
    }


    @Test
    void completeProcessing_shouldRejectConflictingResult_whenPaymentAlreadySucceeded() {
        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        paymentProcessingTransactionService.completeProcessing(
                paymentId,
                new PaymentProviderResult(
                        PaymentProviderOutcome.APPROVED,
                        "SIM-" + paymentId));

        assertThatThrownBy(() -> paymentProcessingTransactionService.completeProcessing(
                        paymentId,
                        new PaymentProviderResult(PaymentProviderOutcome.DECLINED,
                                "SIM-" + paymentId))
        ).isInstanceOf(IllegalStateException.class);

        Payment persistedPayment = paymentRepository.findById(paymentId)
                .orElseThrow();

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
    }

    private Payment persistPendingPayment() {
        Payment pendingPayment = Payment.initiateForBooking(
                UUID.randomUUID(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                "payment-reconciliation-" + UUID.randomUUID(),
                INITIATED_AT);

        return paymentRepository.saveAndFlush(pendingPayment);
    }

    private SimulatedPaymentProvider createSimulatedProvider(String configuredOutcome) {
        return new SimulatedPaymentProvider(simulatedProviderTransactionRepository, configuredOutcome);
    }
}
