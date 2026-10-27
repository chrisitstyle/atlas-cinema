package com.atlascinema.booking.event;

import com.atlascinema.booking.config.BookingKafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingEventPublisherTest {

    private static final Instant OCCURRED_AT = Instant.parse("2030-01-10T12:00:00Z");

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private BookingEventPublisher bookingEventPublisher;

    @BeforeEach
    void setUp() {
        BookingKafkaProperties kafkaProperties = new BookingKafkaProperties("booking-events");

        bookingEventPublisher = new BookingEventPublisher(
                        kafkaTemplate,
                        kafkaProperties);
    }

    @Test
    void publish_shouldSendBookingEventMessageToConfiguredTopic_whenPublishingMessage() {
        UUID bookingId = UUID.randomUUID();

        BookingEventMessage bookingEventMessage = new BookingEventMessage(
                        UUID.randomUUID(),
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        bookingId,
                        OCCURRED_AT,
                        mock(JsonNode.class));

        bookingEventPublisher.publish(bookingEventMessage);

        /*
         * bookingId is used as the Kafka key so all events
         * for the same booking stay assigned to the same partition
         */
        verify(kafkaTemplate).send(
                "booking-events",
                bookingId.toString(),
                bookingEventMessage);
    }
}