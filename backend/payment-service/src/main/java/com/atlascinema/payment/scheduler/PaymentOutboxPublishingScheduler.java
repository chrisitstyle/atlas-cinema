
package com.atlascinema.payment.scheduler;

import com.atlascinema.payment.service.PaymentOutboxPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/*
 * Periodically publishes payment events waiting in the outbox
 * Can be disabled independently from other scheduled tasks
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "payment.outbox.publisher.scheduler",
        name = "enabled",
        havingValue = "true")
public class PaymentOutboxPublishingScheduler {

    private final PaymentOutboxPublisher paymentOutboxPublisher;

    @Scheduled(fixedDelayString = "${payment.outbox.publisher.scheduler.fixed-delay-ms:5000}",
            initialDelayString = "${payment.outbox.publisher.scheduler.initial-delay-ms:5000}")
    public void publishPendingPaymentEvents() {
        int publishedEventCount = paymentOutboxPublisher.publishPendingEvents();

        if (publishedEventCount > 0) {
            log.info("Published {} payment outbox events", publishedEventCount);
        }
    }
}
