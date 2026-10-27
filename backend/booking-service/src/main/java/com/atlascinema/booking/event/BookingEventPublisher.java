package com.atlascinema.booking.event;

import com.atlascinema.booking.config.BookingKafkaProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class BookingEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final BookingKafkaProperties kafkaProperties;

    public CompletableFuture<SendResult<String, Object>> publish(
            BookingEventMessage bookingEventMessage
    ) {
        /*
         * The returned future completes only after Kafka reports
         * whether the message was successfully sent
         */
        return kafkaTemplate.send(
                kafkaProperties.topic(),
                bookingEventMessage.bookingId().toString(),
                bookingEventMessage);
    }
}