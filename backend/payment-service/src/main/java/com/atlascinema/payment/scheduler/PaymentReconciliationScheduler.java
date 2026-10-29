
package com.atlascinema.payment.scheduler;

import com.atlascinema.payment.service.PaymentReconciliationBatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * Periodically starts a bounded payment reconciliation batch
 * Scheduling can be disabled without disabling reconciliation services
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.reconciliation.scheduler",
        name = "enabled",
        havingValue = "true")
public class PaymentReconciliationScheduler {

    private final PaymentReconciliationBatchService paymentReconciliationBatchService;

    @Scheduled(fixedDelayString = "${payment.reconciliation.scheduler.fixed-delay-ms:30000}",
            initialDelayString = "${payment.reconciliation.scheduler.initial-delay-ms:30000}")
    public void reconcileProcessingPayments() {
        int attemptedReconciliations = paymentReconciliationBatchService.reconcileEligiblePayments();

        if (attemptedReconciliations > 0) {
            log.info("Payment reconciliation batch attempted {} payments",
                    attemptedReconciliations);
        }
    }
}
