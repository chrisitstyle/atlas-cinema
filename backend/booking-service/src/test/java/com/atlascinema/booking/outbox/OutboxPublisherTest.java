package com.atlascinema.booking.outbox;

import com.atlascinema.booking.event.BookingEventMessage;
import com.atlascinema.booking.event.BookingEventPublisher;
import com.atlascinema.booking.event.BookingEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2030, 1, 10, 11, 59);

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
    void publishPendingEvents_shouldMarkOutboxEventAsPublished_whenKafkaAcknowledgesMessage()
            throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String serializedMessage = "{}";

        OutboxEvent outboxEvent = OutboxEvent.create(
                        eventId,
                        bookingId,
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        serializedMessage,
                        CREATED_AT);

        BookingEventMessage bookingEventMessage = new BookingEventMessage(
                        eventId,
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        bookingId,
                        NOW,
                        mock(JsonNode.class));

        when(outboxEventRepository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc()
        ).thenReturn(List.of(outboxEvent));

        when(jsonMapper.readValue(serializedMessage, BookingEventMessage.class))
                .thenReturn(bookingEventMessage);

        CompletableFuture<SendResult<String, Object>> kafkaAcknowledgementFuture = CompletableFuture
                .completedFuture(mock(SendResult.class));

        when(bookingEventPublisher.publish(bookingEventMessage)
        ).thenReturn(kafkaAcknowledgementFuture);

        outboxPublisher.publishPendingEvents();

        assertThat(outboxEvent.getPublishedAt())
                .isEqualTo(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC));

        verify(outboxEventRepository).saveAndFlush(outboxEvent);
    }

    @Test
    void publishPendingEvents_shouldKeepOutboxEventUnpublished_whenKafkaPublishingFails() throws Exception {

        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        String serializedMessage = "{}";

        OutboxEvent outboxEvent = OutboxEvent.create(
                        eventId,
                        bookingId,
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        serializedMessage,
                        CREATED_AT);

        BookingEventMessage bookingEventMessage = new BookingEventMessage(
                        eventId,
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        bookingId,
                        NOW,
                        mock(JsonNode.class));

        when(outboxEventRepository
                        .findTop100ByPublishedAtIsNullOrderByCreatedAtAsc())
                .thenReturn(List.of(outboxEvent));

        when(jsonMapper.readValue(serializedMessage, BookingEventMessage.class)).thenReturn(bookingEventMessage);

        CompletableFuture<SendResult<String, Object>>
                failedKafkaPublishingFuture = CompletableFuture.failedFuture(
                        new RuntimeException("Kafka unavailable"));

        when(bookingEventPublisher.publish(bookingEventMessage)).thenReturn(failedKafkaPublishingFuture);

        outboxPublisher.publishPendingEvents();

        assertThat(outboxEvent.getPublishedAt()).isNull();

        verify(outboxEventRepository, never()
        ).saveAndFlush(outboxEvent);
    }
}