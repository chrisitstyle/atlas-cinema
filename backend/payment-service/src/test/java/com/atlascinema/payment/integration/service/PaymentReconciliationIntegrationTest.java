
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.PaymentNotFoundException;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.provider.SimulatedPaymentProvider;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.repository.SimulatedProviderTransactionRepository;
import com.atlascinema.payment.service.PaymentProcessingTransactionService;
import com.atlascinema.payment.service.PaymentReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies recovery of interrupted payments using real PostgreSQL
 * Provider charges and payment outbox results must remain idempotent
 */
class PaymentReconciliationIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    private static final Instant INITIATED_AT = Instant.parse("2026-10-27T10:00:00Z");

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private SimulatedProviderTransactionRepository simulatedProviderTransactionRepository;

    @Autowired
    private PaymentProcessingTransactionService paymentProcessingTransactionService;

    @Autowired
    private PaymentReconciliationService paymentReconciliationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reconcilePayment_shouldRecoverSucceededPayment_whenProviderApprovedCharge() {
        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        // provider charged the customer but the local completion did not commit
        createSimulatedProvider("APPROVED").charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        Payment paymentBeforeRecovery = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(paymentBeforeRecovery.getStatus())
                .isEqualTo(PaymentStatus.PROCESSING);

        Payment recoveredPayment = paymentReconciliationService.reconcilePayment(paymentId);

        assertThat(recoveredPayment.getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(recoveredPayment.getProviderTransactionId())
                .isEqualTo("SIM-" + paymentId);

        assertThat(countProviderCharges(paymentId))
                .isEqualTo(1);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId))
                .singleElement()
                .extracting(event -> event.getEventType())
                .isEqualTo(PaymentOutboxEventType.PAYMENT_SUCCEEDED);
    }

    @Test
    void reconcilePayment_shouldRecoverFailedPayment_whenProviderDeclinedCharge() {
        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        // the provider has already declined the charge
        createSimulatedProvider("DECLINED").charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        Payment recoveredPayment = paymentReconciliationService.reconcilePayment(paymentId);

        assertThat(recoveredPayment.getStatus())
                .isEqualTo(PaymentStatus.FAILED);

        assertThat(recoveredPayment.getProviderTransactionId())
                .isEqualTo("SIM-" + paymentId);

        assertThat(countProviderCharges(paymentId)).isEqualTo(1);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId))
                .singleElement()
                .extracting(event -> event.getEventType())
                .isEqualTo(PaymentOutboxEventType.PAYMENT_FAILED);
    }

    @Test
    void reconcilePayment_shouldRetryCharge_whenProviderHasNoResult() {
        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        // simulate a crash immediately after committing PROCESSING
        paymentProcessingTransactionService.startProcessing(paymentId);

        assertThat(simulatedProviderTransactionRepository
                .findByPaymentId(paymentId))
                .isEmpty();

        assertThat(paymentRepository.findById(paymentId)
                .orElseThrow()
                .getStatus())
                .isEqualTo(PaymentStatus.PROCESSING);

        // recovery must finish the payment instead of leaving it stuck
        Payment recoveredPayment = paymentReconciliationService.reconcilePayment(paymentId);

        assertThat(recoveredPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(recoveredPayment.getProviderTransactionId()).isEqualTo("SIM-" + paymentId);

        assertThat(countProviderCharges(paymentId)).isEqualTo(1);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId))
                .singleElement()
                .extracting(event -> event.getEventType())
                .isEqualTo(PaymentOutboxEventType.PAYMENT_SUCCEEDED);
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

        Instant originalUpdatedAt = paymentRepository
                .findById(paymentId)
                .orElseThrow()
                .getUpdatedAt();

        Payment secondRecovery = paymentReconciliationService.reconcilePayment(paymentId);

        Payment persistedPayment = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(firstRecovery.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(secondRecovery.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(originalUpdatedAt);

        assertThat(countProviderCharges(paymentId)).isEqualTo(1);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId)).hasSize(1);
    }

    @Test
    void reconcilePayment_shouldLeavePendingPaymentUnchanged() {
        Payment pendingPayment = persistPendingPayment();

        Payment reconciledPayment = paymentReconciliationService.reconcilePayment(
                        pendingPayment.getId());

        assertThat(reconciledPayment.getStatus())
                .isEqualTo(PaymentStatus.PENDING);

        assertThat(countProviderCharges(pendingPayment.getId()))
                .isZero();

        assertThat(paymentOutboxRepository
                .findAllByPaymentId(pendingPayment.getId()))
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

        Instant originalUpdatedAt = paymentRepository
                .findById(paymentId)
                .orElseThrow()
                .getUpdatedAt();

        Payment repeatedCompletion = paymentProcessingTransactionService.completeProcessing(
                        paymentId,
                        providerResult);

        Payment persistedPayment = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(firstCompletion.getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(repeatedCompletion.getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(repeatedCompletion.getProviderTransactionId())
                .isEqualTo("SIM-" + paymentId);

        assertThat(persistedPayment.getUpdatedAt()).isEqualTo(originalUpdatedAt);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId)).hasSize(1);
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
                        new PaymentProviderResult(
                                PaymentProviderOutcome.DECLINED,
                                "SIM-" + paymentId))
        ).isInstanceOf(IllegalStateException.class);

        assertThat(paymentRepository.findById(paymentId)
                .orElseThrow()
                .getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId)).hasSize(1);
    }

    @Test
    void reconcilePayment_shouldChargeOnlyOnce_whenTwoRecoveryWorkersRunConcurrently()
            throws Exception {

        Payment pendingPayment = persistPendingPayment();
        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch startRecovery = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            Future<Payment> firstRecovery = executor.submit(() -> {
                workersReady.countDown();
                awaitLatch(startRecovery);

                return paymentReconciliationService.reconcilePayment(paymentId);
            });

            Future<Payment> secondRecovery = executor.submit(() -> {
                workersReady.countDown();
                awaitLatch(startRecovery);

                return paymentReconciliationService.reconcilePayment(paymentId);
            });

            try {
                assertThat(workersReady.await(10, TimeUnit.SECONDS)).isTrue();

                startRecovery.countDown();

                assertThat(firstRecovery.get(20, TimeUnit.SECONDS)
                        .getStatus())
                        .isEqualTo(PaymentStatus.SUCCEEDED);

                assertThat(secondRecovery.get(20, TimeUnit.SECONDS)
                        .getStatus())
                        .isEqualTo(PaymentStatus.SUCCEEDED);

            } finally {
                startRecovery.countDown();
            }
        }

        Payment persistedPayment = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(countProviderCharges(paymentId)).isEqualTo(1);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId)).hasSize(1);
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

    private SimulatedPaymentProvider createSimulatedProvider(
            String configuredOutcome
    ) {
        return new SimulatedPaymentProvider(
                simulatedProviderTransactionRepository,
                configuredOutcome);
    }

    private long countProviderCharges(UUID paymentId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM simulated_provider_transactions
                WHERE payment_id = ?
                """,
                Long.class,
                paymentId);

        return count == null ? 0 : count;
    }

    private static void awaitLatch(CountDownLatch latch) throws InterruptedException {

        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Timed out waiting to begin payment recovery");
        }
    }
}
