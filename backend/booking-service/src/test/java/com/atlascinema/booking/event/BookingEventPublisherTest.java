package com.atlascinema.booking.event;

import com.atlascinema.booking.config.BookingKafkaProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingEventPublisherTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private BookingEventPublisher bookingEventPublisher;

    @BeforeEach
    void setUp() {
        BookingKafkaProperties kafkaProperties = new BookingKafkaProperties("booking-events");

        bookingEventPublisher = new BookingEventPublisher(kafkaTemplate, kafkaProperties);
    }

    @Test
    void publish_shouldSendEventToBookingTopic_whenBookingIsCancelled() {
        UUID bookingId = UUID.randomUUID();

        BookingCancelledEvent event = new BookingCancelledEvent(
                        UUID.randomUUID(),
                        bookingId,
                        UUID.randomUUID(),
                        "john@example.com",
                        UUID.randomUUID(),
                        Instant.parse("2030-01-10T12:00:00Z"));

        bookingEventPublisher.publish(event);

        /*
         * bookingId is used as the Kafka key so events for the same booking
         * stay assigned to the same partition
         */
        verify(kafkaTemplate).send(
                        "booking-events",
                        bookingId.toString(),
                        event);
    }
}
