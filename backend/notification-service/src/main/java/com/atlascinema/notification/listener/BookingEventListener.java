package com.atlascinema.notification.listener;

import com.atlascinema.notification.event.BookingCancelledEvent;
import com.atlascinema.notification.event.BookingCancelledPayload;
import com.atlascinema.notification.event.BookingEventMessage;
import com.atlascinema.notification.event.BookingEventType;
import com.atlascinema.notification.event.BookingExpiredEvent;
import com.atlascinema.notification.event.BookingExpiredPayload;
import com.atlascinema.notification.exception.InvalidBookingEventPayloadException;
import com.atlascinema.notification.exception.UnsupportedBookingEventTypeException;
import com.atlascinema.notification.processed.ProcessedEventRepository;
import com.atlascinema.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.LocalDateTime;

/*
 * Receives booking event messages from Kafka and routes
 * supported event types to their notification handlers
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @KafkaListener(
            topics = "${notification.kafka.booking-events-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void handleBookingEvent(BookingEventMessage bookingEventMessage) {
        BookingEventType bookingEventType = BookingEventType.fromEventName(bookingEventMessage.eventType())
                        .orElseThrow(() -> new UnsupportedBookingEventTypeException(
                                        bookingEventMessage.eventType()));

        LocalDateTime processedAt = LocalDateTime.now(clock);

        /*
         * PostgreSQL atomically decides whether this event
         * has already been processed by this consumer
         */
        int insertedProcessedEventCount = processedEventRepository.insertIfAbsent(
                        bookingEventMessage.eventId(),
                        bookingEventMessage.eventType(),
                        processedAt);

        if (insertedProcessedEventCount == 0) {
            log.info("Skipping already processed booking event {}",
                    bookingEventMessage.eventId());

            return;
        }

        switch (bookingEventType) {
            case BOOKING_CANCELLED ->
                    handleBookingCancelledEvent(bookingEventMessage);

            case BOOKING_EXPIRED -> handleBookingExpiredEvent(bookingEventMessage);
        }
    }

    private void handleBookingCancelledEvent(
            BookingEventMessage bookingEventMessage
    ) {
        BookingCancelledPayload bookingCancelledPayload = convertBookingEventPayload(
                        bookingEventMessage,
                        BookingCancelledPayload.class);

        BookingCancelledEvent bookingCancelledEvent = new BookingCancelledEvent(
                        bookingEventMessage.eventId(),
                        bookingEventMessage.bookingId(),
                        bookingCancelledPayload.customerId(),
                        bookingCancelledPayload.contactEmail(),
                        bookingCancelledPayload.screeningId(),
                        bookingEventMessage.occurredAt());

        notificationService.handleBookingCancelled(bookingCancelledEvent);
    }

    private void handleBookingExpiredEvent(
            BookingEventMessage bookingEventMessage
    ) {
        BookingExpiredPayload bookingExpiredPayload = convertBookingEventPayload(
                        bookingEventMessage,
                        BookingExpiredPayload.class);

        BookingExpiredEvent bookingExpiredEvent = new BookingExpiredEvent(
                        bookingEventMessage.eventId(),
                        bookingEventMessage.bookingId(),
                        bookingExpiredPayload.customerId(),
                        bookingExpiredPayload.contactEmail(),
                        bookingExpiredPayload.screeningId(),
                        bookingEventMessage.occurredAt());

        notificationService.handleBookingExpired(bookingExpiredEvent);
    }

    private <T> T convertBookingEventPayload(
            BookingEventMessage bookingEventMessage,
            Class<T> payloadType
    ) {
        try {
            return jsonMapper.convertValue(
                    bookingEventMessage.payload(),
                    payloadType);

        } catch (IllegalArgumentException exception) {
            throw new InvalidBookingEventPayloadException(
                    bookingEventMessage.eventId(),
                    bookingEventMessage.eventType(),
                    exception);
        }
    }
}