
package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/*
 * Records successful outbox publication in a short database transaction
 * doesnt send messages to Kafka
 */
@Service
@RequiredArgsConstructor
public class PaymentOutboxPublicationService {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final Clock clock;

    @Transactional
    public void markAsPublished(UUID eventId) {
        PaymentOutboxEvent outboxEvent = paymentOutboxRepository
                .findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Payment outbox event %s was not found".formatted(eventId)));

        outboxEvent.markAsPublished(clock.instant());
    }
}
