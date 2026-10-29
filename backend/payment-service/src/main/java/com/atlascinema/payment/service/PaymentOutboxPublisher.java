
package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.event.PaymentResultEventPayload;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/*
 * Publishes pending payment outbox events to Kafka
 * Marks an event as published only after receiving Kafka acknowledgement
 */
@Slf4j
@Service
public class PaymentOutboxPublisher {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentOutboxPublicationService paymentOutboxPublicationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    private final String paymentEventsTopic;
    private final int batchSize;
    private final long sendTimeoutMilliseconds;

    public PaymentOutboxPublisher(
            PaymentOutboxRepository paymentOutboxRepository,
            PaymentOutboxPublicationService paymentOutboxPublicationService,
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${payment.outbox.topic:payment-events}")
            String paymentEventsTopic,
            @Value("${payment.outbox.batch-size:50}")
            int batchSize,
            @Value("${payment.outbox.send-timeout-ms:10000}")
            long sendTimeoutMilliseconds
    ) {
        if (paymentEventsTopic == null || paymentEventsTopic.isBlank()) {
            throw new IllegalArgumentException(
                    "Payment events topic must not be blank");
        }

        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException(
                    "Outbox batch size must be between 1 and 500");
        }

        if (sendTimeoutMilliseconds < 1) {
            throw new IllegalArgumentException(
                    "Kafka send timeout must be positive");
        }

        this.paymentOutboxRepository = paymentOutboxRepository;
        this.paymentOutboxPublicationService = paymentOutboxPublicationService;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.paymentEventsTopic = paymentEventsTopic;
        this.batchSize = batchSize;
        this.sendTimeoutMilliseconds = sendTimeoutMilliseconds;
    }

    public int publishPendingEvents() {
        List<UUID> unpublishedEventIds = paymentOutboxRepository.findUnpublishedEventIds(
                        PageRequest.of(0, batchSize));

        int publishedEventCount = 0;

        for (UUID eventId : unpublishedEventIds) {
            try {
                PaymentOutboxEvent outboxEvent = paymentOutboxRepository
                        .findById(eventId)
                        .orElseThrow(() -> new IllegalStateException(
                                "Payment outbox event %s was not found"
                                        .formatted(eventId)));

                // another publisher may have already completed this event
                if (outboxEvent.getPublishedAt() != null) {
                    continue;
                }

                PaymentResultEventPayload eventPayload = jsonMapper.readValue(
                        outboxEvent.getPayload(),
                        PaymentResultEventPayload.class);

                if (!eventId.equals(eventPayload.eventId())
                        || !outboxEvent.getPaymentId().equals(eventPayload.paymentId())
                        || eventPayload.bookingId() == null
                        || outboxEvent.getEventType() != eventPayload.eventType()) {
                    throw new IllegalStateException(
                            "Payment outbox event %s has inconsistent payload data"
                                    .formatted(eventId));
                }

                // events for the same booking use the same Kafka partition key
                String kafkaMessageKey = eventPayload.bookingId().toString();

                // wait for Kafka acknowledgement before updating PostgreSQL
                kafkaTemplate.send(
                        paymentEventsTopic,
                        kafkaMessageKey,
                        outboxEvent.getPayload()
                ).get(sendTimeoutMilliseconds, TimeUnit.MILLISECONDS);

                paymentOutboxPublicationService.markAsPublished(eventId);

                publishedEventCount++;

            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();

                log.warn("Payment outbox publishing was interrupted for event {}",
                        eventId,
                        interruptedException);

                break;

            } catch (Exception publishingFailure) {
                // Keep the event unpublished so it can be retried
                log.warn("Could not publish payment outbox event {}",
                        eventId,
                        publishingFailure);
            }
        }

        return publishedEventCount;
    }
}
