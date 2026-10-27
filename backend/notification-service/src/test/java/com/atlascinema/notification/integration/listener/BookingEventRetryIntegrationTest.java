package com.atlascinema.notification.integration.listener;

import com.atlascinema.notification.config.NotificationKafkaProperties;
import com.atlascinema.notification.event.BookingCancelledEvent;
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
import static org.mockito.ArgumentMatchers.any;
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
    private NotificationKafkaProperties kafkaProperties;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void bookingCancelled_shouldBePublishedToDlt_whenAllRetriesFail()
            throws Exception {

        BookingCancelledEvent bookingCancelledEvent = new BookingCancelledEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        "guest@example.com",
                        UUID.randomUUID(),
                        NOW);

        /*
         * Every listener attempt fails so the error handler
         * must eventually move the event to the DLT
         */
        doThrow(new IllegalStateException("Notification failed"))
                .when(notificationService)
                .handleBookingCancelled(
                        any(BookingCancelledEvent.class)
                );

        /*
         * Send a real event through Kafka instead of calling
         * the listener directly
         */
        dltKafkaTemplate.send(
                        kafkaProperties.bookingEventsTopic(),
                        bookingCancelledEvent.bookingId().toString(),
                        bookingCancelledEvent).get(5, TimeUnit.SECONDS);

        ConsumerRecord<String, String> dltRecord = consumeFromDlt(
                        bookingCancelledEvent.eventId());

        BookingCancelledEvent dltEvent = jsonMapper.readValue(dltRecord.value(), BookingCancelledEvent.class);

        assertThat(dltEvent.eventId())
                .isEqualTo(bookingCancelledEvent.eventId());

        assertThat(dltEvent.bookingId())
                .isEqualTo(bookingCancelledEvent.bookingId());

        assertThat(dltEvent.contactEmail())
                .isEqualTo(bookingCancelledEvent.contactEmail());

        assertThat(dltEvent.screeningId())
                .isEqualTo(bookingCancelledEvent.screeningId());

        assertThat(dltEvent.occurredAt())
                .isEqualTo(bookingCancelledEvent.occurredAt());

        // The original Kafka key should be preserved in the DLT record
        assertThat(dltRecord.key()).isEqualTo(bookingCancelledEvent.bookingId().toString());

        /*
         * Initial attempt plus two retries means
         * three notification attempts in total
         */
        verify(notificationService,
                times(3)).handleBookingCancelled(
                any(BookingCancelledEvent.class));

        /*
         * Every failed attempt rolls back its database transaction
         * so the event must not be marked as successfully processed
         */
        assertThat(processedEventRepository.existsById(bookingCancelledEvent.eventId())).isFalse();
    }

    private ConsumerRecord<String, String> consumeFromDlt(UUID expectedEventId) throws Exception {

        Properties properties = new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "notification-dlt-test-" + UUID.randomUUID());

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

            consumer.subscribe(List.of(kafkaProperties.bookingEventsDltTopic()));

            Instant deadline = Instant.now().plusSeconds(10);

            while (Instant.now().isBefore(deadline)) {

                for (ConsumerRecord<String, String> record :
                        consumer.poll(
                                Duration.ofMillis(250)
                        )) {

                    BookingCancelledEvent bookingCancelledEvent = jsonMapper.readValue(
                                    record.value(),
                                    BookingCancelledEvent.class
                            );

                    /*
                     * The topic may contain records from earlier tests
                     * so wait for the exact event created by this test
                     */
                    if (bookingCancelledEvent.eventId().equals(expectedEventId)) {
                        return record;
                    }
                }
            }

            throw new AssertionError(
                    "Kafka did not publish event "
                            + expectedEventId
                            + " to the DLT");
        }
    }

    @TestConfiguration
    static class KafkaTopicsConfiguration {

        @Bean
        NewTopic bookingEventsTopic(
                NotificationKafkaProperties kafkaProperties
        ) {
            return TopicBuilder
                    .name(kafkaProperties.bookingEventsTopic())
                    .partitions(1)
                    .replicas(1)
                    .build();
        }

        @Bean
        NewTopic bookingEventsDltTopic(NotificationKafkaProperties kafkaProperties) {
            /*
             * The DLT needs the same partition number because
             * failed records keep their original partition
             */
            return TopicBuilder.name(kafkaProperties.bookingEventsDltTopic())
                    .partitions(1)
                    .replicas(1)
                    .build();
        }
    }
}