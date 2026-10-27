package com.atlascinema.notification.listener;

import com.atlascinema.notification.event.BookingCancelledEvent;
import com.atlascinema.notification.processed.ProcessedEventRepository;
import com.atlascinema.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private static final String BOOKING_CANCELLED = "BookingCancelled";
    private final ProcessedEventRepository processedEventRepository;
    private final Clock clock;
    private final NotificationService notificationService;

    @KafkaListener(
            topics = "${notification.kafka.booking-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void handleBookingCancelled(BookingCancelledEvent bookingCancelledEvent) {
        LocalDateTime processedAt = LocalDateTime.now(clock);

        /*
         * PostgreSQL decides atomically whether this event
         * has already been handled by this consumer
         */
        int inserted = processedEventRepository.insertIfAbsent(
                        bookingCancelledEvent.eventId(),
                        BOOKING_CANCELLED,
                        processedAt);

        if (inserted == 0) {
            log.info("Skipping already processed event {}",
                    bookingCancelledEvent.eventId());

            return;
        }

        notificationService.handleBookingCancelled(bookingCancelledEvent);
    }
}
