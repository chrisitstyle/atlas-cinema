package com.atlascinema.booking.event;

import com.atlascinema.booking.config.BookingKafkaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BookingKafkaProperties kafkaProperties;

    public void publish(
            BookingCancelledEvent event
    ) {
        /*
         * Using bookingId as the key keeps events for the same booking
         * assigned to the same Kafka partition
         */
        kafkaTemplate.send(
                kafkaProperties.topic(),
                event.bookingId().toString(),
                event
        );
    }
}
