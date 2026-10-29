
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.PaymentNotPendingException;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.provider.PaymentProvider;
import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.PaymentProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Verifies that concurrent requests cannot process the same payment twice
 * Uses real PostgreSQL transactions and a controlled payment provider
 */
class PaymentConcurrentProcessingIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    private static final Instant INITIATED_AT = Instant.parse("2026-10-29T10:00:00Z");

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentProvider paymentProvider;

    @Test
    void processPayment_shouldChargeOnlyOnce_whenTwoRequestsOverlap() throws Exception {

        Payment pendingPayment = persistPendingPayment();

        UUID paymentId = pendingPayment.getId();

        String providerTransactionId = "SIM-" + paymentId;

        CountDownLatch providerChargeStarted = new CountDownLatch(1);
        CountDownLatch allowProviderResponse = new CountDownLatch(1);

        // keep the first request inside the provider until the second finishes
        when(paymentProvider.charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE
        )).thenAnswer(invocation -> {

            providerChargeStarted.countDown();

            boolean providerResponseAllowed =
                    allowProviderResponse.await(10, TimeUnit.SECONDS);

            if (!providerResponseAllowed) {
                throw new IllegalStateException("Timed out waiting to complete simulated payment");
            }

            return new PaymentProviderResult(
                    PaymentProviderOutcome.APPROVED,
                    providerTransactionId);
        });

        try (ExecutorService paymentRequestExecutor = Executors.newFixedThreadPool(2)) {

            Future<Payment> firstPaymentResult = paymentRequestExecutor.submit(() ->
                            paymentProcessingService.processPayment(paymentId));

            try {
                // the first request must reach the provider before continuing
                boolean firstRequestReachedProvider = providerChargeStarted.await(10, TimeUnit.SECONDS);

                assertThat(firstRequestReachedProvider).isTrue();

                // the PROCESSING state must already be committed
                Payment processingPayment = paymentRepository
                        .findById(paymentId)
                        .orElseThrow();

                assertThat(processingPayment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);

                assertThat(processingPayment.getProviderTransactionId())
                        .isNull();

                // start a second request while the first provider call is blocked
                Future<Payment> secondPaymentResult = paymentRequestExecutor.submit(() ->
                                paymentProcessingService.processPayment(paymentId));

                // the second request must be rejected before contacting the provider
                assertThatThrownBy(() -> secondPaymentResult.get(10, TimeUnit.SECONDS)
                )
                        .isInstanceOf(ExecutionException.class)
                        .hasCauseInstanceOf(PaymentNotPendingException.class);

                verify(paymentProvider, times(1)).charge(
                        paymentId,
                        PAYMENT_AMOUNT,
                        CURRENCY_CODE);

            } finally {
                // always release the provider even if an assertion fails
                allowProviderResponse.countDown();
            }

            // the first request can now save the successful provider result
            Payment firstProcessedPayment = firstPaymentResult.get(10, TimeUnit.SECONDS);

            assertThat(firstProcessedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

            Payment persistedPayment = paymentRepository
                    .findById(paymentId)
                    .orElseThrow();

            assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

            assertThat(persistedPayment.getProviderTransactionId()).isEqualTo(providerTransactionId);

            assertThat(paymentRepository.count()).isEqualTo(1);

            // two processing requests must produce only one provider call
            verify(paymentProvider, times(1)).charge(
                    paymentId,
                    PAYMENT_AMOUNT,
                    CURRENCY_CODE);
        }
    }

    private Payment persistPendingPayment() {
        Payment pendingPayment = Payment.initiateForBooking(
                UUID.randomUUID(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                "concurrent-processing-" + UUID.randomUUID(),
                INITIATED_AT);

        return paymentRepository.saveAndFlush(pendingPayment);
    }
}
