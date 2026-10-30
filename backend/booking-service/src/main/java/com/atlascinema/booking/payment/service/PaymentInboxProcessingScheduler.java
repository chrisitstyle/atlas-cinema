
package com.atlascinema.booking.payment.service;

import com.atlascinema.booking.payment.repository.PaymentInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/*
 * Retries unprocessed payment events after transient processing failures
 * Each event is handled in its own database transaction
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "booking.payment-inbox.processing",
        name = "enabled",
        havingValue = "true")
public class PaymentInboxProcessingScheduler {

    private final PaymentInboxRepository paymentInboxRepository;
    private final BookingPaymentResultService bookingPaymentResultService;

    @Scheduled(
            fixedDelayString = "${booking.payment-inbox.processing.fixed-delay-ms:5000}",
            initialDelayString = "${booking.payment-inbox.processing.initial-delay-ms:5000}"
    )
    public void processPendingEvents() {
        List<UUID> eventIds = paymentInboxRepository.findUnprocessedEventIds(50);

        for (UUID eventId : eventIds) {
            try {
                bookingPaymentResultService.processEvent(eventId);

            } catch (RuntimeException exception) {
                // a failed event stays in the inbox for a later attempt
                log.warn("Could not process payment inbox event {}",
                        eventId,
                        exception);
            }
        }
    }
}
