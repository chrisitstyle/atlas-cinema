
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.provider.SimulatedPaymentProvider;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.repository.SimulatedProviderTransactionRepository;
import com.atlascinema.payment.service.PaymentReconciliationBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Verifies scheduled payment recovery using real PostgreSQL
 * Tests stale payments, idempotent charge retries and batch limits
 */
@TestPropertySource(properties = {
        "payment.reconciliation.batch-size=2"
})
class PaymentReconciliationBatchIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT =
            new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private SimulatedProviderTransactionRepository simulatedProviderTransactionRepository;

    @Autowired
    private PaymentReconciliationBatchService paymentReconciliationBatchService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void reconcileEligiblePayments_shouldRecoverApprovedPayment() {
        Payment payment = persistProcessingPayment(
                clock.instant().minusSeconds(600));

        UUID paymentId = payment.getId();

        createSimulatedProvider("APPROVED").charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        int attempted = paymentReconciliationBatchService.reconcileEligiblePayments();

        Payment persistedPayment = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(attempted).isEqualTo(1);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getLastReconciliationAttemptAt()).isNotNull();

        assertThat(countProviderCharges(paymentId)).isEqualTo(1);
    }

    @Test
    void reconcileEligiblePayments_shouldRecoverDeclinedPayment() {
        Payment payment = persistProcessingPayment(
                clock.instant().minusSeconds(600));

        UUID paymentId = payment.getId();

        createSimulatedProvider("DECLINED").charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        int attempted = paymentReconciliationBatchService.reconcileEligiblePayments();

        Payment persistedPayment = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(attempted).isEqualTo(1);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(persistedPayment.getProviderTransactionId())
                .isEqualTo("SIM-" + paymentId);

        assertThat(countProviderCharges(paymentId)).isEqualTo(1);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId))
                .singleElement()
                .extracting(event -> event.getEventType())
                .isEqualTo(PaymentOutboxEventType.PAYMENT_FAILED);
    }

    @Test
    void reconcileEligiblePayments_shouldRetryCharge_whenProviderResultIsMissing() {
        Payment payment = persistProcessingPayment(
                clock.instant().minusSeconds(600));

        UUID paymentId = payment.getId();

        // the original worker stopped before calling the provider
        assertThat(countProviderCharges(paymentId)).isZero();

        int firstBatchAttempts = paymentReconciliationBatchService.reconcileEligiblePayments();

        Payment recoveredPayment = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(firstBatchAttempts).isEqualTo(1);

        assertThat(recoveredPayment.getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(recoveredPayment.getLastReconciliationAttemptAt()).isNotNull();

        assertThat(countProviderCharges(paymentId)).isEqualTo(1);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId)).hasSize(1);

        // a terminal payment must not be selected for reconciliation again
        int secondBatchAttempts = paymentReconciliationBatchService.reconcileEligiblePayments();

        assertThat(secondBatchAttempts).isZero();

        assertThat(countProviderCharges(paymentId))
                .isEqualTo(1);
    }

    @Test
    void reconcileEligiblePayments_shouldSkipPayment_whenProcessingStartedRecently() {
        Payment payment = persistProcessingPayment(clock.instant());

        int attempted = paymentReconciliationBatchService.reconcileEligiblePayments();

        Payment persistedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertThat(attempted).isZero();

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

        assertThat(persistedPayment.getLastReconciliationAttemptAt()).isNull();

        // dont retry a charge while the original worker may still be active
        assertThat(countProviderCharges(payment.getId()))
                .isZero();
    }

    @Test
    void reconcileEligiblePayments_shouldRespectBatchSize_andProcessRemainingPaymentsLater() {
        Instant startedAt = clock.instant().minusSeconds(600);

        Payment firstPayment = persistProcessingPayment(startedAt);

        Payment secondPayment = persistProcessingPayment(startedAt);

        Payment thirdPayment = persistProcessingPayment(startedAt);

        // only two payments may be processed in the first batch
        int firstBatchAttempts = paymentReconciliationBatchService.reconcileEligiblePayments();

        int secondBatchAttempts = paymentReconciliationBatchService.reconcileEligiblePayments();

        assertThat(firstBatchAttempts).isEqualTo(2);
        assertThat(secondBatchAttempts).isEqualTo(1);

        assertRecovered(firstPayment.getId());
        assertRecovered(secondPayment.getId());
        assertRecovered(thirdPayment.getId());
    }

    @Test
    void claimForReconciliation_shouldRejectSecondClaim_withinRetryWindow() {
        Payment payment = persistProcessingPayment(
                clock.instant().minusSeconds(600));

        UUID paymentId = payment.getId();
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

        Payment persistedPayment = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(persistedPayment.getLastReconciliationAttemptAt()).isNotNull();

        assertThat(persistedPayment.getStatus())
                .isEqualTo(PaymentStatus.PROCESSING);
    }

    private Payment persistProcessingPayment(
            Instant processingStartedAt
    ) {
        Payment payment = Payment.initiateForBooking(
                UUID.randomUUID(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                "scheduled-reconciliation-" + UUID.randomUUID(),
                processingStartedAt.minusSeconds(60));

        payment.markAsProcessing(processingStartedAt);

        return paymentRepository.saveAndFlush(payment);
    }

    private SimulatedPaymentProvider createSimulatedProvider(
            String configuredOutcome
    ) {
        return new SimulatedPaymentProvider(
                simulatedProviderTransactionRepository,
                configuredOutcome);
    }

    private void assertRecovered(UUID paymentId) {
        Payment persistedPayment = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getLastReconciliationAttemptAt()).isNotNull();

        assertThat(countProviderCharges(paymentId)).isEqualTo(1);

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId))
                .singleElement()
                .extracting(event -> event.getEventType())
                .isEqualTo(PaymentOutboxEventType.PAYMENT_SUCCEEDED);
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
}
