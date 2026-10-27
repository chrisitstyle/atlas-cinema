package com.atlascinema.notification.listener;

import com.atlascinema.notification.event.BookingCancelledEvent;
import com.atlascinema.notification.event.BookingCancelledPayload;
import com.atlascinema.notification.event.BookingEventMessage;
import com.atlascinema.notification.event.BookingEventType;
import com.atlascinema.notification.processed.ProcessedEventRepository;
import com.atlascinema.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingEventListenerTest {

    private static final Instant NOW =
            Instant.parse("2030-01-10T12:00:00Z");

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationService notificationService;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private JsonNode bookingCancelledPayloadNode;

    private BookingEventListener bookingEventListener;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        bookingEventListener = new BookingEventListener(
                        processedEventRepository,
                        notificationService,
                        jsonMapper,
                        clock);
    }

    @Test
    void handleBookingEvent_shouldHandleBookingCancelledNotification_whenEventWasNotProcessedBefore() {
        UUID eventId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();

        BookingEventMessage bookingEventMessage = new BookingEventMessage(
                        eventId,
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        bookingId,
                        NOW,
                        bookingCancelledPayloadNode);

        BookingCancelledPayload bookingCancelledPayload = new BookingCancelledPayload(
                        customerId,
                        "guest@example.com",
                        screeningId);

        BookingCancelledEvent expectedBookingCancelledEvent = new BookingCancelledEvent(
                        eventId,
                        bookingId,
                        customerId,
                        "guest@example.com",
                        screeningId,
                        NOW);

        LocalDateTime processedAt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        when(processedEventRepository.insertIfAbsent(
                        eventId,
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        processedAt)
        ).thenReturn(1);

        when(jsonMapper.convertValue(
                        bookingCancelledPayloadNode,
                        BookingCancelledPayload.class)
        ).thenReturn(bookingCancelledPayload);

        bookingEventListener.handleBookingEvent(bookingEventMessage);

        verify(notificationService).handleBookingCancelled(expectedBookingCancelledEvent);
    }

    @Test
    void handleBookingEvent_shouldSkipBookingCancelledNotification_whenEventWasAlreadyProcessed() {
        UUID eventId = UUID.randomUUID();

        BookingEventMessage bookingEventMessage = new BookingEventMessage(
                        eventId,
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        UUID.randomUUID(),
                        NOW,
                        bookingCancelledPayloadNode);

        LocalDateTime processedAt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        when(processedEventRepository.insertIfAbsent(
                        eventId,
                        BookingEventType.BOOKING_CANCELLED.eventName(),
                        processedAt)).thenReturn(0);

        bookingEventListener.handleBookingEvent(bookingEventMessage);

        verifyNoInteractions(notificationService);
    }
}