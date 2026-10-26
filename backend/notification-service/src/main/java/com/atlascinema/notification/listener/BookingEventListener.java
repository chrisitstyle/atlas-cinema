package com.atlascinema.notification.listener;

import com.atlascinema.notification.event.BookingCancelledEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class BookingEventListener {

    @KafkaListener(
            topics = "${notification.kafka.booking-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void handleBookingCancelled(
            BookingCancelledEvent bookingCancelledEvent
    ) {
        // this method is called asynchronously when Kafka delivers a booking event
        log.info("Received BookingCancelledEvent: {}",
                bookingCancelledEvent);
    }
}
