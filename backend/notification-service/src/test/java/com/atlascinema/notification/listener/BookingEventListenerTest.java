package com.atlascinema.notification.listener;

import com.atlascinema.notification.event.BookingCancelledEvent;
import com.atlascinema.notification.processed.ProcessedEventRepository;
import com.atlascinema.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingEventListenerTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private NotificationService notificationService;

    private BookingEventListener bookingEventListener;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        bookingEventListener = new BookingEventListener(processedEventRepository,
                        clock, notificationService);
    }

    @Test
    void handleBookingCancelled_shouldHandleNotification_whenEventWasNotProcessedBefore() {
        BookingCancelledEvent bookingCancelledEvent = createBookingCancelledEvent();

        LocalDateTime processedAt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        when(processedEventRepository.insertIfAbsent(
                bookingCancelledEvent.eventId(),
                "BookingCancelled",
                processedAt
        )).thenReturn(1);

        bookingEventListener.handleBookingCancelled(bookingCancelledEvent);

        verify(notificationService)
                .handleBookingCancelled(bookingCancelledEvent);
    }

    @Test
    void handleBookingCancelled_shouldSkipNotification_whenEventWasAlreadyProcessed() {
        BookingCancelledEvent bookingCancelledEvent = createBookingCancelledEvent();

        LocalDateTime processedAt = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

        when(processedEventRepository.insertIfAbsent(
                bookingCancelledEvent.eventId(),
                "BookingCancelled",
                processedAt
        )).thenReturn(0);

        bookingEventListener.handleBookingCancelled(bookingCancelledEvent);

        verifyNoInteractions(notificationService);
    }

    private BookingCancelledEvent createBookingCancelledEvent() {
        return new BookingCancelledEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "guest@example.com",
                UUID.randomUUID(),
                NOW);
    }
}
