package com.atlascinema.booking.integration.outbox;

import com.atlascinema.booking.event.BookingCancelledEvent;
import com.atlascinema.booking.event.BookingEventPublisher;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.outbox.OutboxEvent;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import com.atlascinema.booking.outbox.OutboxEventWriter;
import com.atlascinema.booking.outbox.OutboxPublisher;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
    void publishPendingEvents_shouldPublishEventToKafkaAndMarkItAsPublished() {
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

        OutboxEvent beforePublishing = outboxEventRepository
                        .findById(eventId)
                        .orElseThrow();

        assertThat(beforePublishing.getPublishedAt())
                .isNull();

        /*
         * This performs a real Kafka send and waits for the broker acknowledgement
         */
        outboxPublisher.publishPendingEvents();

        OutboxEvent afterPublishing = outboxEventRepository
                        .findById(eventId)
                        .orElseThrow();

        assertThat(afterPublishing.getPublishedAt()).isEqualTo(createdAt);

        BookingCancelledEvent receivedBookingCancelledEvent = consumeEvent(eventId);

        assertThat(receivedBookingCancelledEvent.eventId()).isEqualTo(eventId);

        assertThat(receivedBookingCancelledEvent.bookingId()).isEqualTo(bookingId);

        assertThat(receivedBookingCancelledEvent.contactEmail()).isEqualTo("guest@example.com");

        assertThat(receivedBookingCancelledEvent.screeningId()).isEqualTo(screeningId);

        assertThat(receivedBookingCancelledEvent.occurredAt()).isEqualTo(NOW);
    }

    private BookingCancelledEvent consumeEvent(
            UUID expectedEventId
    ) {
        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "outbox-publisher-test-" + UUID.randomUUID());

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {

            consumer.subscribe(List.of(TOPIC));

            Instant deadline = Instant.now()
                            .plusSeconds(5);

            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, String> record :
                        consumer.poll(Duration.ofMillis(250))) {

                    BookingCancelledEvent bookingCancelledEvent = jsonMapper.readValue(
                                    record.value(),
                                    BookingCancelledEvent.class);

                    /*
                     * Other integration tests may have already written to this topic
                     * so wait specifically for the event created by this test
                     */
                    if (bookingCancelledEvent.eventId().equals(expectedEventId)) {
                        return bookingCancelledEvent;
                    }
                }
            }

            throw new AssertionError("Kafka did not receive event " + expectedEventId);
        }
    }
}