package com.atlascinema.payment.scheduler;

import com.atlascinema.payment.service.PendingPaymentProcessingBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * Periodically starts processing of newly initiated payments
 * Can be disabled independently from payment reconciliation
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.processing.scheduler",
        name = "enabled",
        havingValue = "true")
public class PendingPaymentProcessingScheduler {

    private final PendingPaymentProcessingBatchService batchService;

    @Scheduled(fixedDelayString = "${payment.processing.scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${payment.processing.scheduler.initial-delay-ms:5000}")
    public void processPendingPayments() {
        int processedPaymentCount = batchService.processPendingPayments();

        if (processedPaymentCount > 0) {
            log.info(
                    "Processed {} pending payments",
                    processedPaymentCount);
        }
    }
}