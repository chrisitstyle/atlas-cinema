
package com.atlascinema.booking.integration.payment;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.event.BookingEventMessage;
import com.atlascinema.booking.event.PaymentRefundRequestedPayload;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.outbox.OutboxEvent;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import com.atlascinema.booking.payment.event.PaymentRefundedEvent;
import com.atlascinema.booking.repository.BookingRepository;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Verifies refund confirmations delivered by a real Kafka broker
 * The listener persists the result in PostgreSQL asynchronously
 */
@TestPropertySource(properties = {
        "booking.payment-refunds.topic=payment-refunds-booking-it",
        "booking.payment-refunds.group-id=booking-refunds-kafka-it",
        "booking.payment-refunds.consumer.enabled=true",
        "booking.payment-inbox.processing.enabled=false",
        "spring.kafka.admin.auto-create=true"
})
@Import(PaymentRefundedKafkaIntegrationTest.KafkaTestTopicConfiguration.class)
class PaymentRefundedKafkaIntegrationTest extends AbstractIntegrationTest {

    private static final String REFUNDS_TOPIC = "payment-refunds-booking-it";

    private static final Instant EVENT_TIME = Instant.parse("2030-01-10T12:00:00Z");

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void kafkaListener_shouldPersistRefundConfirmation_whenRealMessageArrives()
            throws Exception {

        UUID paymentId = UUID.randomUUID();

        Booking booking = createBooking();

        String providerTransactionId = "SIM-" + paymentId;
        String providerRefundTransactionId = "SIM-REF-" + paymentId;

        // the booking-service must have requested this refund beforehand
        persistRefundRequest(
                booking.getId(),
                paymentId,
                providerTransactionId);

        PaymentRefundedEvent refundedEvent = new PaymentRefundedEvent(
                UUID.randomUUID(),
                "PAYMENT_REFUNDED",
                paymentId,
                booking.getId(),
                PAYMENT_AMOUNT,
                "PLN",
                providerTransactionId,
                providerRefundTransactionId,
                EVENT_TIME);

        assertThat(countRefundConfirmations(paymentId)).isZero();

        // send the message to the real Kafka broker
        publishRefundedEvent(refundedEvent);

        // The @KafkaListener consumes the message on another thread
        awaitRefundConfirmation(paymentId);

        assertThat(countRefundConfirmations(paymentId))
                .isEqualTo(1);

        String storedRefundReference = jdbcTemplate.queryForObject(
                """
                SELECT provider_refund_transaction_id
                FROM payment_refund_confirmations
                WHERE payment_id = ?
                """,
                String.class,
                paymentId);

        assertThat(storedRefundReference)
                .isEqualTo(providerRefundTransactionId);

        // the refund confirmation does not change the booking status
        assertThat(bookingRepository.findById(booking.getId()))
                .isPresent();
    }

    private Booking createBooking() {
        LocalDateTime createdAt = LocalDateTime.ofInstant(
                EVENT_TIME.minusSeconds(60),
                ZoneOffset.UTC);

        Booking booking = Booking.create(
                null,
                "saga-kafka-integration@example.com",
                UUID.randomUUID(),
                createdAt);

        booking.addSeat(UUID.randomUUID(), PAYMENT_AMOUNT);

        booking.markAsHeld(LocalDateTime.ofInstant(
                        EVENT_TIME.plusSeconds(600),
                        ZoneOffset.UTC));

        return bookingRepository.saveAndFlush(booking);
    }

    private void persistRefundRequest(
            UUID bookingId,
            UUID paymentId,
            String providerTransactionId
    ) {
        PaymentRefundRequestedPayload refundRequest = new PaymentRefundRequestedPayload(
                        paymentId,
                        providerTransactionId,
                        "BOOKING_NOT_CONFIRMABLE");

        BookingEventMessage bookingEvent = new BookingEventMessage(
                UUID.randomUUID(),
                "PaymentRefundRequested",
                bookingId,
                EVENT_TIME,
                jsonMapper.valueToTree(refundRequest));

        OutboxEvent outboxEvent = OutboxEvent.create(
                bookingEvent.eventId(),
                bookingId,
                bookingEvent.eventType(),
                jsonMapper.writeValueAsString(bookingEvent),
                LocalDateTime.ofInstant(EVENT_TIME, ZoneOffset.UTC));

        outboxEventRepository.saveAndFlush(outboxEvent);
    }

    /*
     * Use plain StringSerializer because payment-service sends raw JSON
     * The booking-service producer has a different serializer configuration
     */
    private void publishRefundedEvent(
            PaymentRefundedEvent refundedEvent
    ) throws Exception {

        Properties producerProperties = new Properties();

        producerProperties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());

        producerProperties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());

        producerProperties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class.getName());

        producerProperties.put(
                ProducerConfig.ACKS_CONFIG,
                "all");

        String messageJson = jsonMapper.writeValueAsString(refundedEvent);

        try (KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(producerProperties)) {

            kafkaProducer.send(
                    new ProducerRecord<>(
                            REFUNDS_TOPIC,
                            refundedEvent.bookingId().toString(),
                            messageJson
                    )
            ).get(10, TimeUnit.SECONDS);
        }
    }

    /*
     * wait for the asynchronous listener to commit its database transaction
     */
    private void awaitRefundConfirmation(UUID paymentId)
            throws InterruptedException {

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);

        while (System.nanoTime() < deadline) {
            if (countRefundConfirmations(paymentId) == 1) {
                return;
            }

            Thread.sleep(100);
        }

        throw new AssertionError("Refund confirmation was not persisted for payment "
                + paymentId);
    }

    private long countRefundConfirmations(UUID paymentId) {
        Long count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM payment_refund_confirmations
                WHERE payment_id = ?
                """,
                Long.class,
                paymentId);

        return count;
    }

    /*
     * Create an isolated topic specifically for this Kafka listener test
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTestTopicConfiguration {

        @Bean
        NewTopic paymentRefundsIntegrationTopic() {
            return TopicBuilder.name(REFUNDS_TOPIC)
                    .partitions(1)
                    .replicas(1)
                    .build();
        }
    }
}
