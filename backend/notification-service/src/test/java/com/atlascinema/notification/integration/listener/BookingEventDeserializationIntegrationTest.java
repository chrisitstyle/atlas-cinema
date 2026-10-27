package com.atlascinema.notification.integration.listener;

import com.atlascinema.notification.config.NotificationKafkaProperties;
import com.atlascinema.notification.integration.AbstractIntegrationTest;
import com.atlascinema.notification.processed.ProcessedEventRepository;
import com.atlascinema.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "notification.kafka.retry-interval=50ms",
        "notification.kafka.max-retries=2"
})
class BookingEventDeserializationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private NotificationKafkaProperties kafkaProperties;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void malformedEvent_shouldBePublishedToDlt_withoutCallingListener() throws Exception {
        String key = UUID.randomUUID().toString();

        byte[] malformedPayload =
                """
                {
                  "eventId": definitely-not-valid-json
                }
                """.getBytes(StandardCharsets.UTF_8);

        /*
         * Send raw bytes so the consumer receives a payload
         * that Jackson cannot turn into BookingCancelledEvent
         */
        sendMalformedPayload(
                key,
                malformedPayload
        );

        ConsumerRecord<String, byte[]> dltRecord =
                consumeFromDlt(key);

        /*
         * DLT must preserve the original bytes because
         * deserialization failed before a Java event existed
         */
        assertThat(dltRecord.value())
                .isEqualTo(malformedPayload);

        assertThat(dltRecord.key())
                .isEqualTo(key);

        // The listener never receives an invalid BookingCancelledEvent
        verifyNoInteractions(notificationService);

        // Nothing was successfully processed
        assertThat(processedEventRepository.count())
                .isZero();
    }

    private ConsumerRecord<String, byte[]> consumeFromDlt(
            String expectedKey
    ) {
        Properties properties =
                new Properties();

        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        );

        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "notification-deserialization-dlt-test-"
                        + UUID.randomUUID()
        );

        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        properties.put(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class
        );

        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ByteArrayDeserializer.class
        );

        try (KafkaConsumer<String, byte[]> consumer =
                     new KafkaConsumer<>(properties)) {

            consumer.subscribe(
                    List.of(
                            kafkaProperties.bookingEventsDltTopic()
                    )
            );

            Instant deadline =
                    Instant.now()
                            .plusSeconds(10);

            while (Instant.now().isBefore(deadline)) {
                for (ConsumerRecord<String, byte[]> record :
                        consumer.poll(
                                Duration.ofMillis(250)
                        )) {

                    /*
                     * A malformed payload has no usable eventId
                     * so the unique Kafka key identifies this test record
                     */
                    if (expectedKey.equals(record.key())) {
                        return record;
                    }
                }
            }

            throw new AssertionError(
                    "Kafka did not publish malformed record "
                            + expectedKey
                            + " to the DLT"
            );
        }
    }

    private void sendMalformedPayload(
            String key,
            byte[] payload
    ) throws Exception {

        Properties properties =
                new Properties();

        properties.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()
        );

        properties.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );

        properties.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                ByteArraySerializer.class
        );

        /*
         * This producer intentionally sends raw bytes
         * to simulate a malformed message created outside notification-service
         */
        try (KafkaProducer<String, byte[]> producer =
                     new KafkaProducer<>(properties)) {

            producer.send(
                            new ProducerRecord<>(
                                    kafkaProperties.bookingEventsTopic(),
                                    key,
                                    payload
                            )
                    )
                    .get(
                            5,
                            TimeUnit.SECONDS
                    );
        }
    }
}