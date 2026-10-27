package com.atlascinema.booking.outbox;

import com.atlascinema.booking.event.BookingCancelledEvent;
import com.atlascinema.booking.event.BookingCancelledPayload;
import com.atlascinema.booking.event.BookingEventMessage;
import com.atlascinema.booking.event.BookingEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public void save(
            BookingCancelledEvent bookingCancelledEvent,
            LocalDateTime createdAt
    ) {
        BookingCancelledPayload bookingCancelledPayload = new BookingCancelledPayload(
                        bookingCancelledEvent.customerId(),
                        bookingCancelledEvent.contactEmail(),
                        bookingCancelledEvent.screeningId());

        /*
         * Kafka always receives the same outer message structure
         * while payload contains data specific to this event type
         */
        BookingEventMessage bookingEventMessage = new BookingEventMessage(
                        bookingCancelledEvent.eventId(),
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        bookingCancelledEvent.bookingId(),
                        bookingCancelledEvent.occurredAt(),
                        jsonMapper.valueToTree(bookingCancelledPayload));

        /*
         * Store the complete Kafka message now so publishing later
         * does not depend on the current state of the booking
         */
        String serializedMessage = jsonMapper.writeValueAsString(bookingEventMessage);

        OutboxEvent outboxEvent = OutboxEvent.create(
                        bookingEventMessage.eventId(),
                        bookingEventMessage.bookingId(),
                        bookingEventMessage.eventType(),
                        serializedMessage,
                        createdAt);

        // PostgreSQL stores the event before a separate publisher sends it to Kafka
        outboxEventRepository.save(outboxEvent);
    }
}