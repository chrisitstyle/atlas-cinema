package com.atlascinema.booking.outbox;

import com.atlascinema.booking.event.BookingCancelledEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private JsonMapper jsonMapper;

    private OutboxEventWriter outboxEventWriter;

    @BeforeEach
    void setUp() {
        outboxEventWriter = new OutboxEventWriter(outboxEventRepository, jsonMapper);
    }

    @Test
    void save_shouldStoreBookingCancelledEventInOutbox_whenEventIsValid() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        BookingCancelledEvent bookingCancelledEvent = new BookingCancelledEvent(
                        eventId,
                        bookingId,
                        UUID.randomUUID(),
                        "john@example.com",
                        UUID.randomUUID(),
                        Instant.parse("2030-01-10T12:00:00Z"));

        LocalDateTime createdAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        String payload = """
                {"eventId":"%s"}
                """.formatted(eventId);

        when(jsonMapper.writeValueAsString(bookingCancelledEvent))
                .thenReturn(payload);

        outboxEventWriter.save(bookingCancelledEvent, createdAt);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);

        verify(outboxEventRepository)
                .save(captor.capture());

        OutboxEvent saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(eventId);

        assertThat(saved.getAggregateId()).isEqualTo(bookingId);

        assertThat(saved.getEventType()).isEqualTo("BookingCancelled");

        assertThat(saved.getPayload()).isEqualTo(payload);

        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);

        assertThat(saved.getPublishedAt()).isNull();
    }
}
