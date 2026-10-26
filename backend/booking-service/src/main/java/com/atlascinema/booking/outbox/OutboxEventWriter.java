package com.atlascinema.booking.outbox;

import com.atlascinema.booking.event.BookingCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private static final String BOOKING_CANCELLED = "BookingCancelled";

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public void save(BookingCancelledEvent event, LocalDateTime createdAt) {
        // store the complete event now so it does not depend on future booking changes
        String payload = jsonMapper.writeValueAsString(event);

        OutboxEvent outboxEvent = OutboxEvent.create(
                        event.eventId(),
                        event.bookingId(),
                        BOOKING_CANCELLED,
                        payload,
                        createdAt);

        // the event is stored in PostgreSQL first and will be published to Kafka later
        outboxEventRepository.save(outboxEvent);
    }
}
