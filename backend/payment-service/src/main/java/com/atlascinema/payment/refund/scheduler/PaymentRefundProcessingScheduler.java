
package com.atlascinema.payment.refund.scheduler;

import com.atlascinema.payment.refund.repository.PaymentRefundInboxRepository;
import com.atlascinema.payment.refund.service.PaymentRefundProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/*
 * Retries pending refund requests without blocking newer inbox events
 * Claims each event before contacting the refund processing service
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "payment.refund.processing.scheduler",
        name = "enabled",
        havingValue = "true")
public class PaymentRefundProcessingScheduler {

    private final PaymentRefundInboxRepository refundInboxRepository;
    private final PaymentRefundProcessingService refundProcessingService;
    private final Clock clock;
    private final int batchSize;
    private final long retryDelaySeconds;

    public PaymentRefundProcessingScheduler(
            PaymentRefundInboxRepository refundInboxRepository,
            PaymentRefundProcessingService refundProcessingService,
            Clock clock,
            @Value("${payment.refund.processing.batch-size:50}") int batchSize,
            @Value("${payment.refund.processing.retry-delay-seconds:30}")
            long retryDelaySeconds
    ) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Refund processing batch size must be between 1 and 500");
        }

        if (retryDelaySeconds < 1) {
            throw new IllegalArgumentException("Refund processing retry delay must be positive");
        }

        this.refundInboxRepository = refundInboxRepository;
        this.refundProcessingService = refundProcessingService;
        this.clock = clock;
        this.batchSize = batchSize;
        this.retryDelaySeconds = retryDelaySeconds;
    }

    @Scheduled(
            fixedDelayString = "${payment.refund.processing.scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${payment.refund.processing.scheduler.initial-delay-ms:5000}"
    )
    public void processPendingRefunds() {
        Instant attemptedAt = clock.instant();

        Instant retryEligibleBefore = attemptedAt.minusSeconds(retryDelaySeconds);

        List<UUID> candidateEventIds = refundInboxRepository.findEligibleEventIds(
                retryEligibleBefore,
                batchSize);

        for (UUID eventId : candidateEventIds) {
            try {
                boolean claimed = refundInboxRepository.claimForProcessing(
                        eventId,
                        clock.instant(),
                        retryEligibleBefore);

                if (!claimed) {
                    continue;
                }

                refundProcessingService.processRefundRequest(eventId);

            } catch (Exception exception) {
                // the inbox event remains available for a later retry
                log.warn("Could not process refund request event {}",
                        eventId,
                        exception);
            }
        }
    }
}
