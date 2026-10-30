
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.PendingPaymentProcessingBatchService;
import com.atlascinema.payment.service.PaymentProcessingTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Verifies automatic processing of pending payments using real PostgreSQL
 * Ensures concurrent workers cannot charge one payment twice
 */
class PendingPaymentProcessingIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private PendingPaymentProcessingBatchService batchService;

    @Autowired
    private PaymentProcessingTransactionService processingTransactionService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    @Test
    void processPendingPayments_shouldCompletePaymentAndCreateOutboxEvent() {
        Payment payment = createPendingPayment();

        int processedCount = batchService.processPendingPayments();

        Payment persistedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertThat(processedCount).isEqualTo(1);

        assertThat(persistedPayment.getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getProviderTransactionId()).isNotBlank();

        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAllByPaymentId(payment.getId());

        assertThat(outboxEvents)
                .hasSize(1);

        assertThat(outboxEvents.getFirst().getEventType())
                .isEqualTo(PaymentOutboxEventType.PAYMENT_SUCCEEDED);

        assertThat(outboxEvents.getFirst().getPublishedAt())
                .isNull();

        assertThat(countProviderCharges(payment.getId()))
                .isEqualTo(1);
    }

    @Test
    void processPendingPayments_shouldSkipAlreadyProcessedPayments() {
        Payment payment = createPendingPayment();

        int firstProcessedCount = batchService.processPendingPayments();

        int secondProcessedCount = batchService.processPendingPayments();

        assertThat(firstProcessedCount).isEqualTo(1);
        assertThat(secondProcessedCount).isZero();

        assertThat(paymentRepository
                .findById(payment.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(countProviderCharges(payment.getId()))
                .isEqualTo(1);

        assertThat(paymentOutboxRepository
                .findAllByPaymentId(payment.getId()))
                .hasSize(1);
    }

    @Test
    void processPendingPayments_shouldNotProcessPaymentAlreadyInProcessingStatus() {
        Payment pendingPayment = createPendingPayment();

        Payment processingPayment = createPendingPayment();

        // simulate a payment already claimed by another processing worker
        processingTransactionService.startProcessing(processingPayment.getId());

        int processedCount = batchService.processPendingPayments();

        assertThat(processedCount).isEqualTo(1);

        assertThat(paymentRepository
                .findById(pendingPayment.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(paymentRepository
                .findById(processingPayment.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(PaymentStatus.PROCESSING);

        assertThat(countProviderCharges(pendingPayment.getId())).isEqualTo(1);

        assertThat(countProviderCharges(processingPayment.getId())).isZero();

        assertThat(paymentOutboxRepository
                .findAllByPaymentId(processingPayment.getId()))
                .isEmpty();
    }

    @Test
    void processPendingPayments_shouldNotChargeTwice_whenTwoWorkersRunConcurrently()
            throws Exception {

        Payment payment = createPendingPayment();

        CountDownLatch workersReady = new CountDownLatch(2);
        CountDownLatch startProcessing = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {

            Future<Integer> firstWorker = executor.submit(() -> {
                workersReady.countDown();

                if (!startProcessing.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("First worker did not start");
                }

                return batchService.processPendingPayments();
            });

            Future<Integer> secondWorker = executor.submit(() -> {
                workersReady.countDown();

                if (!startProcessing.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Second worker did not start");
                }

                return batchService.processPendingPayments();
            });

            try {
                assertThat(workersReady.await(10, TimeUnit.SECONDS)).isTrue();

                startProcessing.countDown();

                int firstProcessedCount = firstWorker.get(20, TimeUnit.SECONDS);

                int secondProcessedCount = secondWorker.get(20, TimeUnit.SECONDS);

                assertThat(firstProcessedCount + secondProcessedCount).isEqualTo(1);

            } finally {
                startProcessing.countDown();
            }
        }

        Payment persistedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        // the provider stores only one charge for this payment
        assertThat(countProviderCharges(payment.getId()))
                .isEqualTo(1);

        // exactly one definitive payment result is created
        assertThat(paymentOutboxRepository
                .findAllByPaymentId(payment.getId()))
                .hasSize(1);
    }

    private Payment createPendingPayment() {
        Payment payment = Payment.initiateForBooking(
                UUID.randomUUID(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                "pending-processing-" + UUID.randomUUID(),
                clock.instant());

        return paymentRepository.saveAndFlush(payment);
    }

    private long countProviderCharges(UUID paymentId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM simulated_provider_transactions
                WHERE payment_id = ?
                """,
                Long.class,
                paymentId
        );

        return count;
    }
}
