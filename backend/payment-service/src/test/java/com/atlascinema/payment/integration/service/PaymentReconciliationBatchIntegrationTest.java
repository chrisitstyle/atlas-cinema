
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.provider.SimulatedPaymentProvider;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.repository.SimulatedProviderTransactionRepository;
import com.atlascinema.payment.service.PaymentReconciliationBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Verifies scheduled payment recovery using real PostgreSQL
 * Tests eligibility, retry delays, and bounded batch processing
 */
@TestPropertySource(properties = {
        "payment.reconciliation.batch-size=2"
})
class PaymentReconciliationBatchIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SimulatedProviderTransactionRepository simulatedProviderTransactionRepository;

    @Autowired
    private PaymentReconciliationBatchService paymentReconciliationBatchService;

    @Autowired
    private Clock clock;

    @Test
    void reconcileEligiblePayments_shouldRecoverApprovedPayment() {
        Payment processingPayment = persistProcessingPayment(
                clock.instant().minusSeconds(600));

        UUID paymentId = processingPayment.getId();

        createSimulatedProvider("APPROVED").charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        int attemptedReconciliations = paymentReconciliationBatchService.reconcileEligiblePayments();

        Payment persistedPayment = paymentRepository.findById(paymentId).orElseThrow();

        assertThat(attemptedReconciliations).isEqualTo(1);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getProviderTransactionId()).isEqualTo("SIM-" + paymentId);

        assertThat(persistedPayment.getLastReconciliationAttemptAt()).isNotNull();
    }

    @Test
    void reconcileEligiblePayments_shouldRecoverDeclinedPayment() {
        Payment processingPayment = persistProcessingPayment(
                clock.instant().minusSeconds(600));

        UUID paymentId = processingPayment.getId();

        createSimulatedProvider("DECLINED").charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        int attemptedReconciliations = paymentReconciliationBatchService.reconcileEligiblePayments();

        Payment persistedPayment = paymentRepository.findById(paymentId).orElseThrow();

        assertThat(attemptedReconciliations).isEqualTo(1);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(persistedPayment.getProviderTransactionId()).isEqualTo("SIM-" + paymentId);
    }

    @Test
    void reconcileEligiblePayments_shouldDelayRetry_whenProviderResultIsMissing() {
        Payment processingPayment = persistProcessingPayment(
                clock.instant().minusSeconds(600));

        UUID paymentId = processingPayment.getId();

        int firstBatchAttempts = paymentReconciliationBatchService.reconcileEligiblePayments();

        Payment paymentAfterFirstAttempt = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        Instant firstAttemptAt = paymentAfterFirstAttempt.getLastReconciliationAttemptAt();

        int secondBatchAttempts = paymentReconciliationBatchService.reconcileEligiblePayments();

        Payment paymentAfterSecondAttempt = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(firstBatchAttempts).isEqualTo(1);

        // the retry delay prevents an immediate second provider lookup
        assertThat(secondBatchAttempts).isZero();

        assertThat(paymentAfterSecondAttempt.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        assertThat(paymentAfterSecondAttempt.getLastReconciliationAttemptAt()).isEqualTo(firstAttemptAt);

        assertThat(paymentAfterSecondAttempt.getProviderTransactionId()).isNull();
    }

    @Test
    void reconcileEligiblePayments_shouldSkipPayment_whenProcessingStartedRecently() {
        Payment processingPayment = persistProcessingPayment(clock.instant());

        int attemptedReconciliations = paymentReconciliationBatchService.reconcileEligiblePayments();

        Payment persistedPayment = paymentRepository
                .findById(processingPayment.getId())
                .orElseThrow();

        assertThat(attemptedReconciliations).isZero();

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        assertThat(persistedPayment.getLastReconciliationAttemptAt()).isNull();
    }

    @Test
    void reconcileEligiblePayments_shouldRespectBatchSize_andProcessRemainingPaymentsLater() {
        Instant processingStartedAt = clock.instant().minusSeconds(600);

        Payment firstPayment = persistProcessingPayment(processingStartedAt);

        Payment secondPayment = persistProcessingPayment(processingStartedAt);

        Payment thirdPayment = persistProcessingPayment(processingStartedAt);

        // batch size is configured to 2 for this test class
        int firstBatchAttempts = paymentReconciliationBatchService.reconcileEligiblePayments();

        int secondBatchAttempts = paymentReconciliationBatchService.reconcileEligiblePayments();

        assertThat(firstBatchAttempts).isEqualTo(2);
        assertThat(secondBatchAttempts).isEqualTo(1);

        assertThat(paymentRepository.findById(firstPayment.getId())
                .orElseThrow().getLastReconciliationAttemptAt())
                .isNotNull();

        assertThat(paymentRepository.findById(secondPayment.getId())
                .orElseThrow().getLastReconciliationAttemptAt())
                .isNotNull();

        assertThat(paymentRepository.findById(thirdPayment.getId())
                .orElseThrow().getLastReconciliationAttemptAt())
                .isNotNull();
    }

    @Test
    void claimForReconciliation_shouldRejectSecondClaim_withinRetryWindow() {
        Payment processingPayment = persistProcessingPayment(
                clock.instant().minusSeconds(600));

        UUID paymentId = processingPayment.getId();

        Instant currentTime = clock.instant();

        Instant processingStartedBefore = currentTime.minusSeconds(60);

        Instant retryEligibleBefore = currentTime.minusSeconds(120);

        int firstClaim = paymentRepository.claimForReconciliation(
                paymentId,
                PaymentStatus.PROCESSING,
                processingStartedBefore,
                retryEligibleBefore,
                currentTime);

        int repeatedClaim = paymentRepository.claimForReconciliation(
                paymentId,
                PaymentStatus.PROCESSING,
                processingStartedBefore,
                retryEligibleBefore,
                currentTime);

        assertThat(firstClaim).isEqualTo(1);
        assertThat(repeatedClaim).isZero();
    }

    private Payment persistProcessingPayment(Instant processingStartedAt) {
        Payment payment = Payment.initiateForBooking(
                UUID.randomUUID(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                "scheduled-reconciliation-" + UUID.randomUUID(),
                processingStartedAt.minusSeconds(60));

        payment.markAsProcessing(processingStartedAt);

        return paymentRepository.saveAndFlush(payment);
    }

    private SimulatedPaymentProvider createSimulatedProvider(String configuredOutcome) {
        return new SimulatedPaymentProvider(
                simulatedProviderTransactionRepository,
                configuredOutcome);
    }
}
