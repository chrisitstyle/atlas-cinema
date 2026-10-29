
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.event.PaymentResultEventPayload;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import com.atlascinema.payment.service.PaymentOutboxPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Verifies payment outbox publishing using real PostgreSQL
 * Simulates Kafka acknowledgements and failures without a broker
 */
class PaymentOutboxPublisherIntegrationTest extends AbstractIntegrationTest {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private static final Instant EVENT_OCCURRED_AT = Instant.parse("2026-10-29T10:00:00Z");

    @Autowired
    private PaymentOutboxPublisher paymentOutboxPublisher;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Test
    void publishPendingEvents_shouldMarkEventAsPublished_whenKafkaAcknowledges()
            throws Exception {

        StoredTestEvent storedTestEvent = persistUnpublishedEvent(EVENT_OCCURRED_AT);

        PaymentOutboxEvent outboxEvent = storedTestEvent.outboxEvent();

        when(kafkaTemplate.send(
                PAYMENT_EVENTS_TOPIC,
                storedTestEvent.bookingId().toString(),
                outboxEvent.getPayload()
        )).thenReturn(CompletableFuture.completedFuture(null));

        int publishedEventCount = paymentOutboxPublisher.publishPendingEvents();

        PaymentOutboxEvent persistedEvent = paymentOutboxRepository
                .findById(outboxEvent.getId())
                .orElseThrow();

        assertThat(publishedEventCount).isEqualTo(1);

        assertThat(persistedEvent.getPublishedAt()).isNotNull();

        verify(kafkaTemplate).send(
                PAYMENT_EVENTS_TOPIC,
                storedTestEvent.bookingId().toString(),
                outboxEvent.getPayload());
    }

    @Test
    void publishPendingEvents_shouldKeepEventUnpublished_whenKafkaFails()
            throws Exception {

        StoredTestEvent storedTestEvent = persistUnpublishedEvent(EVENT_OCCURRED_AT);

        PaymentOutboxEvent outboxEvent = storedTestEvent.outboxEvent();

        CompletableFuture<SendResult<String, String>> failedAcknowledgement = new CompletableFuture<>();

        failedAcknowledgement.completeExceptionally(new IllegalStateException("Kafka broker unavailable"));

        when(kafkaTemplate.send(
                PAYMENT_EVENTS_TOPIC,
                storedTestEvent.bookingId().toString(),
                outboxEvent.getPayload()
        )).thenReturn(failedAcknowledgement);

        int publishedEventCount = paymentOutboxPublisher.publishPendingEvents();

        PaymentOutboxEvent persistedEvent = paymentOutboxRepository
                .findById(outboxEvent.getId())
                .orElseThrow();

        assertThat(publishedEventCount).isZero();

        //  failed send must remain eligible for another publishing attempt
        assertThat(persistedEvent.getPublishedAt()).isNull();
    }

    @Test
    void publishPendingEvents_shouldNotSendEventAgain_whenAlreadyPublished()
            throws Exception {

        StoredTestEvent storedTestEvent = persistUnpublishedEvent(EVENT_OCCURRED_AT);

        PaymentOutboxEvent outboxEvent = storedTestEvent.outboxEvent();

        when(kafkaTemplate.send(
                PAYMENT_EVENTS_TOPIC,
                storedTestEvent.bookingId().toString(),
                outboxEvent.getPayload()
        )).thenReturn(CompletableFuture.completedFuture(null));

        int firstPublishedEventCount = paymentOutboxPublisher.publishPendingEvents();

        int secondPublishedEventCount = paymentOutboxPublisher.publishPendingEvents();

        assertThat(firstPublishedEventCount).isEqualTo(1);
        assertThat(secondPublishedEventCount).isZero();

        verify(kafkaTemplate, times(1)).send(
                PAYMENT_EVENTS_TOPIC,
                storedTestEvent.bookingId().toString(),
                outboxEvent.getPayload());

        assertThat(paymentOutboxRepository
                .findById(outboxEvent.getId())
                .orElseThrow()
                .getPublishedAt())
                .isNotNull();
    }

    @Test
    void publishPendingEvents_shouldWaitForAcknowledgement_beforeMarkingEventAsPublished()
            throws Exception {

        StoredTestEvent storedTestEvent = persistUnpublishedEvent(EVENT_OCCURRED_AT);

        PaymentOutboxEvent outboxEvent = storedTestEvent.outboxEvent();

        CountDownLatch kafkaSendStarted = new CountDownLatch(1);

        CompletableFuture<SendResult<String, String>> pendingAcknowledgement = new CompletableFuture<>();

        when(kafkaTemplate.send(
                PAYMENT_EVENTS_TOPIC,
                storedTestEvent.bookingId().toString(),
                outboxEvent.getPayload()
        )).thenAnswer(invocation -> {
            kafkaSendStarted.countDown();
            return pendingAcknowledgement;
        });

        try (ExecutorService publishingExecutor = Executors.newSingleThreadExecutor()) {

            Future<Integer> publishingResult = publishingExecutor.submit(
                            paymentOutboxPublisher::publishPendingEvents);

            try {
                assertThat(kafkaSendStarted.await(5, TimeUnit.SECONDS)).isTrue();

                // the broker has not acknowledged the event yet
                PaymentOutboxEvent eventBeforeAcknowledgement =
                        paymentOutboxRepository
                                .findById(outboxEvent.getId())
                                .orElseThrow();

                assertThat(eventBeforeAcknowledgement.getPublishedAt()).isNull();

            } finally {
                // always release the publishing thread
                pendingAcknowledgement.complete(null);
            }

            assertThat(publishingResult.get(10, TimeUnit.SECONDS))
                    .isEqualTo(1);
        }

        PaymentOutboxEvent eventAfterAcknowledgement = paymentOutboxRepository
                        .findById(outboxEvent.getId())
                        .orElseThrow();

        assertThat(eventAfterAcknowledgement.getPublishedAt()).isNotNull();
    }

    @Test
    void publishPendingEvents_shouldContinue_whenOneEventFails()
            throws Exception {

        StoredTestEvent firstStoredEvent = persistUnpublishedEvent(EVENT_OCCURRED_AT);

        StoredTestEvent secondStoredEvent = persistUnpublishedEvent(EVENT_OCCURRED_AT.plusSeconds(1));

        CompletableFuture<SendResult<String, String>> failedAcknowledgement = new CompletableFuture<>();

        failedAcknowledgement.completeExceptionally(new IllegalStateException("First event was rejected"));

        when(kafkaTemplate.send(
                PAYMENT_EVENTS_TOPIC,
                firstStoredEvent.bookingId().toString(),
                firstStoredEvent.outboxEvent().getPayload()
        )).thenReturn(failedAcknowledgement);

        when(kafkaTemplate.send(
                PAYMENT_EVENTS_TOPIC,
                secondStoredEvent.bookingId().toString(),
                secondStoredEvent.outboxEvent().getPayload()
        )).thenReturn(CompletableFuture.completedFuture(null));

        int publishedEventCount = paymentOutboxPublisher.publishPendingEvents();

        PaymentOutboxEvent firstPersistedEvent = paymentOutboxRepository
                .findById(firstStoredEvent.outboxEvent().getId())
                .orElseThrow();

        PaymentOutboxEvent secondPersistedEvent = paymentOutboxRepository
                .findById(secondStoredEvent.outboxEvent().getId())
                .orElseThrow();

        assertThat(publishedEventCount).isEqualTo(1);

        assertThat(firstPersistedEvent.getPublishedAt()).isNull();

        assertThat(secondPersistedEvent.getPublishedAt()).isNotNull();
    }

    private StoredTestEvent persistUnpublishedEvent(Instant occurredAt)
            throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        PaymentResultEventPayload eventPayload = new PaymentResultEventPayload(
                        eventId,
                        PaymentOutboxEventType.PAYMENT_SUCCEEDED,
                        paymentId,
                        bookingId,
                        new BigDecimal("29.90"),
                        "PLN",
                        "SIM-" + paymentId,
                        occurredAt);

        String eventPayloadJson = jsonMapper.writeValueAsString(eventPayload);

        PaymentOutboxEvent outboxEvent = PaymentOutboxEvent.create(
                eventId,
                paymentId,
                PaymentOutboxEventType.PAYMENT_SUCCEEDED,
                eventPayloadJson,
                occurredAt);

        PaymentOutboxEvent persistedEvent = paymentOutboxRepository.saveAndFlush(outboxEvent);

        return new StoredTestEvent(persistedEvent, bookingId);
    }

    private record StoredTestEvent(PaymentOutboxEvent outboxEvent, UUID bookingId) {
    }
}
