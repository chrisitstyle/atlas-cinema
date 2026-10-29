
package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * verifies that one reconciliation failure does not stop the batch
 */
@ExtendWith(MockitoExtension.class)
class PaymentReconciliationBatchServiceTest {

    private static final Instant CURRENT_TIME = Instant.parse("2026-10-29T10:00:00Z");

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentReconciliationService paymentReconciliationService;

    @Test
    void reconcileEligiblePayments_shouldContinue_whenOnePaymentFails() {
        UUID firstPaymentId = UUID.randomUUID();
        UUID secondPaymentId = UUID.randomUUID();

        Clock fixedClock = Clock.fixed(CURRENT_TIME, ZoneOffset.UTC);

        PaymentReconciliationBatchService batchService = new PaymentReconciliationBatchService(
                        paymentRepository,
                        paymentReconciliationService,
                        fixedClock,
                        10,
                        60,
                        120);

        when(paymentRepository.findReconciliationCandidateIds(
                eq(PaymentStatus.PROCESSING),
                any(Instant.class),
                any(Instant.class),
                any(Pageable.class)
        )).thenReturn(List.of(firstPaymentId, secondPaymentId));

        when(paymentRepository.claimForReconciliation(
                any(UUID.class),
                eq(PaymentStatus.PROCESSING),
                any(Instant.class),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(1);

        when(paymentReconciliationService.reconcilePayment(firstPaymentId))
                .thenThrow(new IllegalStateException("Provider lookup failed"));

        int attemptedReconciliations = batchService.reconcileEligiblePayments();

        assertThat(attemptedReconciliations).isEqualTo(2);

        verify(paymentReconciliationService).reconcilePayment(firstPaymentId);

        // the second payment must still be processed
        verify(paymentReconciliationService).reconcilePayment(secondPaymentId);
    }
}
