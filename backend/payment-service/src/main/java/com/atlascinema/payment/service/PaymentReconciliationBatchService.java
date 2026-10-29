
package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 * Reconciles a limited batch of old processing payments
 * Records each attempt before querying the payment provider
 */
@Slf4j
@Service
public class PaymentReconciliationBatchService {

    private final PaymentRepository paymentRepository;
    private final PaymentReconciliationService paymentReconciliationService;
    private final Clock clock;

    private final int batchSize;
    private final long minimumProcessingAgeSeconds;
    private final long retryDelaySeconds;

    public PaymentReconciliationBatchService(
            PaymentRepository paymentRepository,
            PaymentReconciliationService paymentReconciliationService,
            Clock clock,
            @Value("${payment.reconciliation.batch-size:50}") int batchSize,
            @Value("${payment.reconciliation.minimum-processing-age-seconds:60}")
            long minimumProcessingAgeSeconds,
            @Value("${payment.reconciliation.retry-delay-seconds:120}")
            long retryDelaySeconds
    ) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Reconciliation batch size must be between 1 and 500");
        }

        if (minimumProcessingAgeSeconds < 0 || retryDelaySeconds < 1) {
            throw new IllegalArgumentException("Reconciliation timing values are invalid");
        }

        this.paymentRepository = paymentRepository;
        this.paymentReconciliationService = paymentReconciliationService;
        this.clock = clock;
        this.batchSize = batchSize;
        this.minimumProcessingAgeSeconds = minimumProcessingAgeSeconds;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    public int reconcileEligiblePayments() {
        Instant currentTime = clock.instant();

        Instant processingStartedBefore = currentTime.minusSeconds(minimumProcessingAgeSeconds);

        Instant retryEligibleBefore = currentTime.minusSeconds(retryDelaySeconds);

        List<UUID> candidatePaymentIds = paymentRepository.findReconciliationCandidateIds(
                        PaymentStatus.PROCESSING,
                        processingStartedBefore,
                        retryEligibleBefore,
                        PageRequest.of(0, batchSize));

        int attemptedReconciliations = 0;

        for (UUID paymentId : candidatePaymentIds) {
            try {
                int claimedPayments = paymentRepository.claimForReconciliation(
                        paymentId,
                        PaymentStatus.PROCESSING,
                        processingStartedBefore,
                        retryEligibleBefore,
                        currentTime
                );

                // another scheduler may have claimed or completed this payment
                if (claimedPayments == 0) {
                    continue;
                }

                attemptedReconciliations++;

                paymentReconciliationService.reconcilePayment(paymentId);

            } catch (Exception reconciliationFailure) {
                // one failed payment must not stop the remaining batch
                log.warn("Could not reconcile payment {}",
                        paymentId,
                        reconciliationFailure);
            }
        }

        return attemptedReconciliations;
    }
}
