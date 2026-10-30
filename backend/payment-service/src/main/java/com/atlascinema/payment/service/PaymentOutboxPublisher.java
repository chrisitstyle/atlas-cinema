
package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.event.PaymentRefundedEventPayload;
import com.atlascinema.payment.event.PaymentResultEventPayload;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/*
 * Publishes payment and refund events from the transactional outbox
 * Marks messages as published only after Kafka acknowledgement
 */
@Slf4j
@Service
public class PaymentOutboxPublisher {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentOutboxPublicationService paymentOutboxPublicationService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    private final String paymentEventsTopic;
    private final String paymentRefundsTopic;
    private final int batchSize;
    private final long sendTimeoutMilliseconds;

    public PaymentOutboxPublisher(
            PaymentOutboxRepository paymentOutboxRepository,
            PaymentOutboxPublicationService paymentOutboxPublicationService,
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper,
            @Value("${payment.outbox.topic:payment-events}")
            String paymentEventsTopic,
            @Value("${payment.refund.events.topic:payment-refunds}")
            String paymentRefundsTopic,
            @Value("${payment.outbox.batch-size:50}")
            int batchSize,
            @Value("${payment.outbox.send-timeout-ms:10000}")
            long sendTimeoutMilliseconds
    ) {
        if (paymentEventsTopic == null || paymentEventsTopic.isBlank()) {
            throw new IllegalArgumentException("Payment events topic must not be blank");
        }

        if (paymentRefundsTopic == null || paymentRefundsTopic.isBlank()) {
            throw new IllegalArgumentException("Payment refunds topic must not be blank");
        }

        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Outbox batch size must be between 1 and 500");
        }

        if (sendTimeoutMilliseconds < 1) {
            throw new IllegalArgumentException("Kafka send timeout must be positive");
        }

        this.paymentOutboxRepository = paymentOutboxRepository;
        this.paymentOutboxPublicationService = paymentOutboxPublicationService;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.paymentEventsTopic = paymentEventsTopic;
        this.paymentRefundsTopic = paymentRefundsTopic;
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

                if (outboxEvent.getPublishedAt() != null) {
                    continue;
                }

                PublicationDestination destination = resolveDestination(outboxEvent);

                // the booking id remains the Kafka partition key
                kafkaTemplate.send(
                        destination.topic(),
                        destination.bookingId().toString(),
                        outboxEvent.getPayload()
                ).get(
                        sendTimeoutMilliseconds,
                        TimeUnit.MILLISECONDS);

                // update PostgreSQL only after Kafka acknowledges the message
                paymentOutboxPublicationService.markAsPublished(eventId);

                publishedEventCount++;

            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();

                log.warn("Payment outbox publication interrupted for event {}",
                        eventId,
                        exception);

                break;

            } catch (Exception exception) {
                // a failed event remains unpublished and can be retried
                log.warn(
                        "Could not publish payment outbox event {}",
                        eventId,
                        exception);
            }
        }

        return publishedEventCount;
    }

    private PublicationDestination resolveDestination(
            PaymentOutboxEvent outboxEvent
    ) {
        return switch (outboxEvent.getEventType()) {
            case PAYMENT_SUCCEEDED, PAYMENT_FAILED -> {
                PaymentResultEventPayload payload = jsonMapper.readValue(
                        outboxEvent.getPayload(),
                        PaymentResultEventPayload.class);

                validateEventIdentity(
                        outboxEvent,
                        payload.eventId(),
                        payload.eventType(),
                        payload.paymentId(),
                        payload.bookingId());

                yield new PublicationDestination(paymentEventsTopic, payload.bookingId());
            }

            case PAYMENT_REFUNDED -> {
                PaymentRefundedEventPayload payload = jsonMapper.readValue(
                        outboxEvent.getPayload(),
                        PaymentRefundedEventPayload.class);

                validateEventIdentity(
                        outboxEvent,
                        payload.eventId(),
                        payload.eventType(),
                        payload.paymentId(),
                        payload.bookingId());

                if (payload.providerRefundTransactionId() == null
                        || payload.providerRefundTransactionId().isBlank()) {
                    throw new IllegalStateException("Refund event %s is missing provider refund reference"
                                    .formatted(outboxEvent.getId()));
                }

                yield new PublicationDestination(paymentRefundsTopic, payload.bookingId());
            }
        };
    }

    private void validateEventIdentity(
            PaymentOutboxEvent outboxEvent,
            UUID eventId,
            com.atlascinema.payment.domain.PaymentOutboxEventType eventType,
            UUID paymentId,
            UUID bookingId
    ) {
        if (!Objects.equals(outboxEvent.getId(), eventId)
                || !Objects.equals(outboxEvent.getPaymentId(), paymentId)
                || outboxEvent.getEventType() != eventType
                || bookingId == null) {
            throw new IllegalStateException(
                    "Payment outbox event %s has inconsistent payload data"
                            .formatted(outboxEvent.getId()));
        }
    }

    private record PublicationDestination(
            String topic,
            UUID bookingId
    ) {
    }
}
