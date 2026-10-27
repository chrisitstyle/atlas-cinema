package com.atlascinema.booking.outbox;

import com.atlascinema.booking.event.BookingCancelledEvent;
import com.atlascinema.booking.event.BookingCancelledPayload;
import com.atlascinema.booking.event.BookingEventMessage;
import com.atlascinema.booking.event.BookingEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

    private static final Instant OCCURRED_AT = Instant.parse("2030-01-10T12:00:00Z");

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2030, 1, 10, 12, 0);

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private JsonNode bookingCancelledPayloadNode;

    private OutboxEventWriter outboxEventWriter;

    @BeforeEach
    void setUp() {
        outboxEventWriter = new OutboxEventWriter(outboxEventRepository, jsonMapper);
    }

    @Test
    void save_shouldStoreBookingCancelledMessageInOutbox_whenBookingCancelledEventIsProvided() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();

        BookingCancelledEvent bookingCancelledEvent = new BookingCancelledEvent(
                        eventId,
                        bookingId,
                        customerId,
                        "john@example.com",
                        screeningId,
                        OCCURRED_AT);

        String serializedBookingEventMessage = """
                {"eventId":"%s"}
                """.formatted(eventId);

        when(jsonMapper.valueToTree(any(BookingCancelledPayload.class))).thenReturn(bookingCancelledPayloadNode);

        when(jsonMapper.writeValueAsString(any(BookingEventMessage.class))
        ).thenReturn(serializedBookingEventMessage);

        outboxEventWriter.saveCancelled(bookingCancelledEvent, CREATED_AT);

        ArgumentCaptor<BookingCancelledPayload> bookingCancelledPayloadCaptor = ArgumentCaptor.forClass(BookingCancelledPayload.class);

        verify(jsonMapper).valueToTree(bookingCancelledPayloadCaptor.capture());

        BookingCancelledPayload bookingCancelledPayload = bookingCancelledPayloadCaptor.getValue();

        assertThat(bookingCancelledPayload.customerId()).isEqualTo(customerId);

        assertThat(bookingCancelledPayload.contactEmail()).isEqualTo("john@example.com");

        assertThat(bookingCancelledPayload.screeningId()).isEqualTo(screeningId);

        ArgumentCaptor<BookingEventMessage> bookingEventMessageCaptor = ArgumentCaptor.forClass(BookingEventMessage.class);

        verify(jsonMapper).writeValueAsString(bookingEventMessageCaptor.capture());

        BookingEventMessage bookingEventMessage = bookingEventMessageCaptor.getValue();

        assertThat(bookingEventMessage.eventId()).isEqualTo(eventId);

        assertThat(bookingEventMessage.eventType()).isEqualTo(BookingEventType.BOOKING_CANCELLED.eventName());

        assertThat(bookingEventMessage.bookingId()).isEqualTo(bookingId);

        assertThat(bookingEventMessage.occurredAt()).isEqualTo(OCCURRED_AT);

        assertThat(bookingEventMessage.payload()).isSameAs(bookingCancelledPayloadNode);

        ArgumentCaptor<OutboxEvent> outboxEventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository).save(outboxEventCaptor.capture());

        OutboxEvent savedOutboxEvent = outboxEventCaptor.getValue();

        assertThat(savedOutboxEvent.getId()).isEqualTo(eventId);

        assertThat(savedOutboxEvent.getAggregateId()).isEqualTo(bookingId);

        assertThat(savedOutboxEvent.getEventType()).isEqualTo(BookingEventType.BOOKING_CANCELLED.eventName());

        assertThat(savedOutboxEvent.getPayload()).isEqualTo(serializedBookingEventMessage);

        assertThat(savedOutboxEvent.getCreatedAt()).isEqualTo(CREATED_AT);

        assertThat(savedOutboxEvent.getPublishedAt()).isNull();
    }
}