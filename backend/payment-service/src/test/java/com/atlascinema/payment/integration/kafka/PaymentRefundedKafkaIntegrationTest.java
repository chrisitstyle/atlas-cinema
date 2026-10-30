
package com.atlascinema.payment.integration.kafka;

import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.event.PaymentRefundedEventPayload;
import com.atlascinema.payment.integration.AbstractKafkaIntegrationTest;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import com.atlascinema.payment.service.PaymentOutboxPublisher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Verifies delivery of refund completion events to a real Kafka broker
 * Uses the shared PostgreSQL and Kafka Testcontainers infrastructure
 */
class PaymentRefundedKafkaIntegrationTest extends AbstractKafkaIntegrationTest {

    private static final String REFUNDS_TOPIC = "payment-refunds";

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private PaymentOutboxPublisher paymentOutboxPublisher;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void publishPendingEvents_shouldSendRefundedEventToRefundsTopic() {
        UUID eventId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        PaymentRefundedEventPayload eventPayload = new PaymentRefundedEventPayload(
                        eventId,
                        PaymentOutboxEventType.PAYMENT_REFUNDED,
                        paymentId,
                        bookingId,
                        new BigDecimal("29.90"),
                        "PLN",
                        "SIM-" + paymentId,
                        "SIM-REF-" + paymentId,
                        Instant.now());

        String payloadJson = jsonMapper.writeValueAsString(eventPayload);

        PaymentOutboxEvent outboxEvent = PaymentOutboxEvent.create(
                eventId,
                paymentId,
                PaymentOutboxEventType.PAYMENT_REFUNDED,
                payloadJson,
                eventPayload.occurredAt());

        paymentOutboxRepository.saveAndFlush(outboxEvent);

        try (KafkaConsumer<String, String> consumer = createConsumer()) {
            consumer.subscribe(List.of(REFUNDS_TOPIC));

            int publishedCount = paymentOutboxPublisher.publishPendingEvents();

            assertThat(publishedCount).isEqualTo(1);

            ConsumerRecord<String, String> receivedRecord = receiveEvent(consumer, eventId);

            assertThat(receivedRecord.topic()).isEqualTo(REFUNDS_TOPIC);

            assertThat(receivedRecord.key()).isEqualTo(bookingId.toString());

            PaymentRefundedEventPayload receivedPayload = jsonMapper.readValue(
                            receivedRecord.value(),
                            PaymentRefundedEventPayload.class);

            assertThat(receivedPayload.eventId()).isEqualTo(eventId);

            assertThat(receivedPayload.eventType())
                    .isEqualTo(PaymentOutboxEventType.PAYMENT_REFUNDED);

            assertThat(receivedPayload.paymentId()).isEqualTo(paymentId);

            assertThat(receivedPayload.bookingId()).isEqualTo(bookingId);

            assertThat(receivedPayload.providerRefundTransactionId()).isEqualTo("SIM-REF-" + paymentId);
        }

        PaymentOutboxEvent publishedEvent = paymentOutboxRepository
                .findById(eventId)
                .orElseThrow();

        assertThat(publishedEvent.getPublishedAt()).isNotNull();

        assertThat(paymentOutboxPublisher.publishPendingEvents()).isZero();
    }

    private KafkaConsumer<String, String> createConsumer() {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "payment-refunded-test-" + UUID.randomUUID());

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());

        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");

        properties.put(
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                false);

        return new KafkaConsumer<>(properties);
    }

    private ConsumerRecord<String, String> receiveEvent(
            KafkaConsumer<String, String> consumer,
            UUID expectedEventId
    ) {
        Instant deadline = Instant.now().plusSeconds(20);

        while (Instant.now().isBefore(deadline)) {
            var records = consumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, String> record : records) {
                PaymentRefundedEventPayload payload = jsonMapper.readValue(
                                record.value(),
                                PaymentRefundedEventPayload.class);

                if (expectedEventId.equals(payload.eventId())) {
                    return record;
                }
            }
        }

        throw new AssertionError("Kafka did not deliver refund event " + expectedEventId);
    }
}
