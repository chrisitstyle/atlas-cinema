package com.atlascinema.booking.integration.outbox;

import com.atlascinema.booking.event.BookingCancelledEvent;
import com.atlascinema.booking.event.BookingCancelledPayload;
import com.atlascinema.booking.event.BookingEventMessage;
import com.atlascinema.booking.event.BookingEventPublisher;
import com.atlascinema.booking.event.BookingEventType;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.outbox.OutboxEvent;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import com.atlascinema.booking.outbox.OutboxEventWriter;
import com.atlascinema.booking.outbox.OutboxPublisher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPublisherIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    private static final String TOPIC = "booking-events";

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxEventWriter outboxEventWriter;

    @Autowired
    private BookingEventPublisher bookingEventPublisher;

    @Autowired
    private JsonMapper jsonMapper;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();

        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        /*
         * The scheduled bean is disabled in tests
         * so this test controls exactly when publishing happens
         */
        outboxPublisher = new OutboxPublisher(
                        outboxEventRepository,
                        bookingEventPublisher,
                        jsonMapper,
                        clock);
    }

    @Test
    void publishPendingEvents_shouldPublishBookingEventMessageToKafkaAndMarkOutboxEventAsPublished() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();

        BookingCancelledEvent bookingCancelledEvent = new BookingCancelledEvent(
                        eventId,
                        bookingId,
                        null,
                        "guest@example.com",
                        screeningId,
                        NOW);

        LocalDateTime createdAt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        outboxEventWriter.save(bookingCancelledEvent, createdAt);

        OutboxEvent outboxEventBeforePublishing = outboxEventRepository
                        .findById(eventId)
                        .orElseThrow();

        assertThat(outboxEventBeforePublishing.getPublishedAt()
        ).isNull();

        /*
         * This performs a real Kafka send and waits
         * for the broker acknowledgement
         */
        outboxPublisher.publishPendingEvents();

        OutboxEvent outboxEventAfterPublishing = outboxEventRepository
                        .findById(eventId)
                        .orElseThrow();

        assertThat(outboxEventAfterPublishing.getPublishedAt()
        ).isEqualTo(createdAt);

        BookingEventMessage receivedBookingEventMessage = consumeBookingEventMessage(eventId);

        assertThat(receivedBookingEventMessage.eventId()).isEqualTo(eventId);

        assertThat(receivedBookingEventMessage.eventType())
                .isEqualTo(BookingEventType.BOOKING_CANCELLED.eventName());

        assertThat(receivedBookingEventMessage.bookingId()
        ).isEqualTo(bookingId);

        assertThat(receivedBookingEventMessage.occurredAt()).isEqualTo(NOW);

        BookingCancelledPayload receivedBookingCancelledPayload = jsonMapper.convertValue(
                        receivedBookingEventMessage.payload(),
                        BookingCancelledPayload.class
        );

        assertThat(receivedBookingCancelledPayload.customerId()).isNull();

        assertThat(receivedBookingCancelledPayload.contactEmail()).isEqualTo("guest@example.com");

        assertThat(receivedBookingCancelledPayload.screeningId()).isEqualTo(screeningId);
    }

    private BookingEventMessage consumeBookingEventMessage(
            UUID expectedEventId
    ) {
        Properties consumerProperties = new Properties();

        consumerProperties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());

        consumerProperties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-publisher-test-" + UUID.randomUUID());

        consumerProperties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");

        consumerProperties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        consumerProperties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        try (KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(consumerProperties)) {

            kafkaConsumer.subscribe(List.of(TOPIC));

            Instant pollingDeadline = Instant.now().plusSeconds(5);

            while (Instant.now().isBefore(pollingDeadline)) {
                for (ConsumerRecord<String, String> kafkaRecord :
                        kafkaConsumer.poll(Duration.ofMillis(250))) {

                    BookingEventMessage bookingEventMessage = jsonMapper.readValue(
                                    kafkaRecord.value(),
                                    BookingEventMessage.class);

                    /*
                     * Other integration tests may have already written
                     * to this topic so wait for this exact event
                     */
                    if (bookingEventMessage.eventId()
                            .equals(expectedEventId)) {

                        return bookingEventMessage;
                    }
                }
            }

            throw new AssertionError("Kafka did not receive booking event "
                            + expectedEventId);
        }
    }
}