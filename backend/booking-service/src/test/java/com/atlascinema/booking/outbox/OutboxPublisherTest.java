package com.atlascinema.booking.outbox;

import com.atlascinema.booking.event.BookingCancelledEvent;
import com.atlascinema.booking.event.BookingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private BookingEventPublisher bookingEventPublisher;

    @Mock
    private JsonMapper jsonMapper;

    private Clock clock;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);

        outboxPublisher = new OutboxPublisher(
                        outboxEventRepository,
                        bookingEventPublisher,
                        jsonMapper,
                        clock);
    }

    @Test
    void publishPendingEvents_shouldMarkEventAsPublished_whenKafkaAcknowledgesMessage()
            throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String payload = "{}";

        OutboxEvent outboxEvent = OutboxEvent.create(
                        eventId,
                        bookingId,
                        BookingCancelledEvent.TYPE,
                        payload,
                        LocalDateTime.of(2030, 1, 10, 11, 59));

        BookingCancelledEvent bookingCancelledEvent = new BookingCancelledEvent(
                        eventId,
                        bookingId,
                        null,
                        "guest@example.com",
                        UUID.randomUUID(),
                        NOW);

        when(outboxEventRepository
                .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(outboxEvent));

        when(jsonMapper.readValue(
                payload,
                BookingCancelledEvent.class
        )).thenReturn(bookingCancelledEvent);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(mock(SendResult.class));

        when(bookingEventPublisher.publish(bookingCancelledEvent))
                .thenReturn(future);

        outboxPublisher.publishPendingEvents();

        assertThat(outboxEvent.getPublishedAt()).isEqualTo(
                        LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        verify(outboxEventRepository).saveAndFlush(outboxEvent);
    }

    @Test
    void publishPendingEvents_shouldKeepEventUnpublished_whenKafkaPublishingFails() throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String payload = "{}";

        OutboxEvent outboxEvent = OutboxEvent.create(
                        eventId,
                        bookingId,
                        BookingCancelledEvent.TYPE,
                        payload,
                        LocalDateTime.of(2030, 1, 10, 11, 59));

        BookingCancelledEvent bookingCancelledEvent = new BookingCancelledEvent(
                        eventId,
                        bookingId,
                        null,
                        "guest@example.com",
                        UUID.randomUUID(),
                        NOW);

        when(outboxEventRepository
                .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(outboxEvent));

        when(jsonMapper.readValue(payload, BookingCancelledEvent.class
        )).thenReturn(bookingCancelledEvent);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.failedFuture(
                        new RuntimeException("Kafka unavailable"));

        when(bookingEventPublisher.publish(bookingCancelledEvent)).thenReturn(future);

        outboxPublisher.publishPendingEvents();

        assertThat(outboxEvent.getPublishedAt()).isNull();

        verify(outboxEventRepository, never()).saveAndFlush(outboxEvent);
    }
}
