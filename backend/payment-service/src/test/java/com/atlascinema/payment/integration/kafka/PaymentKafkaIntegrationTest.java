
package com.atlascinema.payment.integration.kafka;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.event.PaymentResultEventPayload;
import com.atlascinema.payment.integration.AbstractKafkaIntegrationTest;
import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.PaymentOutboxPublisher;
import com.atlascinema.payment.service.PaymentProcessingTransactionService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
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
 * Verifies real payment event delivery through Kafka and PostgreSQL
 * Uses shared Testcontainers infrastructure from AbstractKafkaIntegrationTest
 */
class PaymentKafkaIntegrationTest extends AbstractKafkaIntegrationTest {

    private static final String PAYMENT_EVENTS_TOPIC = "payment-events";

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    private static final Instant PAYMENT_INITIATED_AT = Instant.parse("2026-10-27T10:00:00Z");

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private PaymentProcessingTransactionService paymentProcessingTransactionService;

    @Autowired
    private PaymentOutboxPublisher paymentOutboxPublisher;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void publishPendingEvents_shouldDeliverSucceededEvent_toKafka()
            throws Exception {

        verifyPaymentEventDelivery(
                PaymentProviderOutcome.APPROVED,
                PaymentOutboxEventType.PAYMENT_SUCCEEDED,
                PaymentStatus.SUCCEEDED);
    }

    @Test
    void publishPendingEvents_shouldDeliverFailedEvent_toKafka()
            throws Exception {

        verifyPaymentEventDelivery(
                PaymentProviderOutcome.DECLINED,
                PaymentOutboxEventType.PAYMENT_FAILED,
                PaymentStatus.FAILED);
    }

    /*
     * Creates a payment, completes it, and consumes the real Kafka message
     */
    private void verifyPaymentEventDelivery(
            PaymentProviderOutcome providerOutcome,
            PaymentOutboxEventType expectedEventType,
            PaymentStatus expectedPaymentStatus
    ) throws Exception {

        UUID bookingId = UUID.randomUUID();

        Payment pendingPayment = Payment.initiateForBooking(
                bookingId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                "kafka-integration-" + UUID.randomUUID(),
                PAYMENT_INITIATED_AT);

        paymentRepository.saveAndFlush(pendingPayment);

        UUID paymentId = pendingPayment.getId();

        // commit PROCESSING before storing the final payment result
        paymentProcessingTransactionService.startProcessing(paymentId);

        paymentProcessingTransactionService.completeProcessing(paymentId,
                new PaymentProviderResult(providerOutcome,
                        "SIM-" + paymentId));

        Payment completedPayment = paymentRepository
                .findById(paymentId)
                .orElseThrow();

        List<PaymentOutboxEvent> storedEvents = paymentOutboxRepository.findAllByPaymentId(paymentId);

        assertThat(completedPayment.getStatus()).isEqualTo(expectedPaymentStatus);

        assertThat(storedEvents).hasSize(1);

        PaymentOutboxEvent outboxEvent = storedEvents.getFirst();

        assertThat(outboxEvent.getEventType()).isEqualTo(expectedEventType);

        // the event must be unpublished before the publisher runs
        assertThat(outboxEvent.getPublishedAt()).isNull();

        try (KafkaConsumer<String, String> kafkaConsumer = createKafkaConsumer()) {

            kafkaConsumer.subscribe(List.of(PAYMENT_EVENTS_TOPIC));

            // publish using the real Spring KafkaTemplate
            int publishedEventCount = paymentOutboxPublisher.publishPendingEvents();

            assertThat(publishedEventCount).isEqualTo(1);

            ConsumerRecord<String, String> receivedRecord = receiveEvent(kafkaConsumer, outboxEvent.getId());

            assertThat(receivedRecord.topic()).isEqualTo(PAYMENT_EVENTS_TOPIC);

            // messages belonging to one booking share the same Kafka key
            assertThat(receivedRecord.key()).isEqualTo(bookingId.toString());

            PaymentResultEventPayload receivedPayload = jsonMapper.readValue(
                            receivedRecord.value(),
                            PaymentResultEventPayload.class);

            assertThat(receivedPayload.eventId()).isEqualTo(outboxEvent.getId());

            assertThat(receivedPayload.eventType()).isEqualTo(expectedEventType);

            assertThat(receivedPayload.paymentId()).isEqualTo(paymentId);

            assertThat(receivedPayload.bookingId()).isEqualTo(bookingId);

            assertThat(receivedPayload.amount()).isEqualByComparingTo(PAYMENT_AMOUNT);

            assertThat(receivedPayload.currencyCode()).isEqualTo(CURRENCY_CODE);

            assertThat(receivedPayload.providerTransactionId()).isEqualTo("SIM-" + paymentId);

            assertThat(receivedPayload.occurredAt()).isNotNull();
        }

        // Kafka acknowledged the event, so PostgreSQL must mark it published
        PaymentOutboxEvent publishedEvent = paymentOutboxRepository
                .findById(outboxEvent.getId())
                .orElseThrow();

        assertThat(publishedEvent.getPublishedAt())
                .isNotNull();

        // a subsequent run must not publish the completed event again
        int repeatedPublishedEventCount = paymentOutboxPublisher.publishPendingEvents();

        assertThat(repeatedPublishedEventCount).isZero();
    }

    /*
     * Creates an independent Kafka consumer group for every test
     */
    private KafkaConsumer<String, String> createKafkaConsumer() {
        Properties consumerProperties = new Properties();

        consumerProperties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());

        consumerProperties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "payment-kafka-integration-" + UUID.randomUUID());

        consumerProperties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class.getName());

        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        consumerProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        return new KafkaConsumer<>(consumerProperties);
    }

    /*
     * Polls Kafka until the expected event appears or the timeout expires
     */
    private ConsumerRecord<String, String> receiveEvent(
            KafkaConsumer<String, String> kafkaConsumer,
            UUID expectedEventId
    ) throws Exception {

        Instant deadline = Instant.now().plusSeconds(20);

        while (Instant.now().isBefore(deadline)) {

            ConsumerRecords<String, String> receivedRecords = kafkaConsumer.poll(Duration.ofSeconds(1));

            for (ConsumerRecord<String, String> receivedRecord : receivedRecords) {

                PaymentResultEventPayload eventPayload = jsonMapper.readValue(
                                receivedRecord.value(),
                                PaymentResultEventPayload.class);

                if (expectedEventId.equals(eventPayload.eventId())) {
                    return receivedRecord;
                }
            }
        }

        throw new AssertionError("Kafka did not deliver payment event " + expectedEventId);
    }
}
