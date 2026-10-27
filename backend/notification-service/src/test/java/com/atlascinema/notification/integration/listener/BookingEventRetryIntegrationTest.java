package com.atlascinema.notification.integration.listener;

import com.atlascinema.notification.config.NotificationKafkaProperties;
import com.atlascinema.notification.event.BookingCancelledEvent;
import com.atlascinema.notification.event.BookingCancelledPayload;
import com.atlascinema.notification.event.BookingEventMessage;
import com.atlascinema.notification.event.BookingEventType;
import com.atlascinema.notification.integration.AbstractIntegrationTest;
import com.atlascinema.notification.processed.ProcessedEventRepository;
import com.atlascinema.notification.service.NotificationService;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "notification.kafka.retry-interval=50ms",
        "notification.kafka.max-retries=2"
})
@Import(BookingEventRetryIntegrationTest.KafkaTopicsConfiguration.class)
class BookingEventRetryIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Autowired
    @Qualifier("dltKafkaTemplate")
    private KafkaTemplate<String, Object> dltKafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private NotificationKafkaProperties notificationKafkaProperties;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void bookingCancelled_shouldBePublishedToDlt_whenAllRetriesFail() throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();

        BookingCancelledPayload bookingCancelledPayload = new BookingCancelledPayload(
                        null,
                        "guest@example.com",
                        screeningId);

        BookingEventMessage bookingEventMessage = new BookingEventMessage(
                        eventId,
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        bookingId,
                        NOW,
                        jsonMapper.valueToTree(bookingCancelledPayload));

        BookingCancelledEvent expectedBookingCancelledEvent = new BookingCancelledEvent(
                        eventId,
                        bookingId,
                        null,
                        "guest@example.com",
                        screeningId,
                        NOW);

        /*
         * Every listener attempt fails so the error handler
         * must eventually move the message to the DLT
         */
        doThrow(new IllegalStateException(
                        "Notification failed")).when(notificationService)
                .handleBookingCancelled(
                        expectedBookingCancelledEvent);

        /*
         * Send a real booking event message through Kafka
         * instead of calling the listener directly
         */
        dltKafkaTemplate.send(
                        notificationKafkaProperties.bookingEventsTopic(),
                        bookingId.toString(),
                        bookingEventMessage).get(5, TimeUnit.SECONDS);

        ConsumerRecord<String, String> deadLetterRecord = consumeBookingEventFromDlt(bookingId);

        BookingEventMessage deadLetterBookingEventMessage = jsonMapper.readValue(
                        deadLetterRecord.value(),
                        BookingEventMessage.class);

        assertThat(deadLetterBookingEventMessage.eventId()).isEqualTo(eventId);

        assertThat(deadLetterBookingEventMessage.eventType()).isEqualTo(BookingEventType.BOOKING_CANCELLED.eventName());

        assertThat(deadLetterBookingEventMessage.bookingId()).isEqualTo(bookingId);

        assertThat(deadLetterBookingEventMessage.occurredAt()).isEqualTo(NOW);

        BookingCancelledPayload deadLetterBookingCancelledPayload = jsonMapper.convertValue(
                        deadLetterBookingEventMessage.payload(),
                        BookingCancelledPayload.class);

        assertThat(deadLetterBookingCancelledPayload.customerId()).isNull();

        assertThat(deadLetterBookingCancelledPayload.contactEmail()).isEqualTo("guest@example.com");

        assertThat(deadLetterBookingCancelledPayload.screeningId()).isEqualTo(screeningId);

        /*
         * The original Kafka key must be preserved
         * in the dead letter record
         */
        assertThat(deadLetterRecord.key()).isEqualTo(bookingId.toString());

        /*
         * Initial attempt plus two retries means
         * three notification attempts in total
         */
        verify(notificationService, times(3)).handleBookingCancelled(
                expectedBookingCancelledEvent);

        /*
         * Every failed attempt rolls back its database transaction
         * so the event must not be marked as successfully processed
         */
        assertThat(processedEventRepository.existsById(eventId)).isFalse();
    }

    private ConsumerRecord<String, String> consumeBookingEventFromDlt(
            UUID expectedBookingId
    ) {
        Properties consumerProperties = new Properties();

        consumerProperties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());

        consumerProperties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "notification-dlt-test-" + UUID.randomUUID());

        consumerProperties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");

        consumerProperties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        consumerProperties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);

        try (KafkaConsumer<String, String> deadLetterConsumer = new KafkaConsumer<>(consumerProperties)) {

            deadLetterConsumer.subscribe(
                    List.of(notificationKafkaProperties.bookingEventsDltTopic()));

            Instant pollingDeadline = Instant.now().plusSeconds(10);

            while (Instant.now().isBefore(pollingDeadline)) {
                for (ConsumerRecord<String, String> deadLetterRecord :
                        deadLetterConsumer.poll(
                                Duration.ofMillis(250)
                        )) {

                    /*
                     * The DLT may contain records from other tests,
                     * including malformed records that cannot be deserialized
                     */
                    if (expectedBookingId
                            .toString()
                            .equals(deadLetterRecord.key())) {

                        return deadLetterRecord;
                    }
                }
            }

            throw new AssertionError("Kafka did not publish booking event for booking "
                            + expectedBookingId
                            + " to the DLT");
        }
    }

    @TestConfiguration
    static class KafkaTopicsConfiguration {

        @Bean
        NewTopic bookingEventsTopic(NotificationKafkaProperties notificationKafkaProperties) {
            return TopicBuilder
                    .name(notificationKafkaProperties.bookingEventsTopic())
                    .partitions(1)
                    .replicas(1)
                    .build();
        }

        @Bean
        NewTopic bookingEventsDltTopic(
                NotificationKafkaProperties notificationKafkaProperties
        ) {
            /*
             * The DLT needs the same partition number because
             * failed records keep their original partition
             */
            return TopicBuilder
                    .name(notificationKafkaProperties.bookingEventsDltTopic())
                    .partitions(1)
                    .replicas(1)
                    .build();
        }
    }
}