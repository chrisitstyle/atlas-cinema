package com.atlascinema.notification.integration.listener;

import com.atlascinema.notification.event.BookingCancelledEvent;
import com.atlascinema.notification.integration.AbstractIntegrationTest;
import com.atlascinema.notification.listener.BookingEventListener;
import com.atlascinema.notification.processed.ProcessedEvent;
import com.atlascinema.notification.processed.ProcessedEventRepository;
import com.atlascinema.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BookingEventListenerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Autowired
    private BookingEventListener bookingEventListener;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void handleBookingCancelled_shouldHandleEventOnlyOnce_whenSameEventIsDeliveredTwice() {
        BookingCancelledEvent bookingCancelledEvent = createCancelledEvent();

        bookingEventListener.handleBookingCancelled(bookingCancelledEvent);

        bookingEventListener.handleBookingCancelled(bookingCancelledEvent);

        // the same event id can exist only once in processed_events
        assertThat(processedEventRepository.count())
                .isEqualTo(1);

        ProcessedEvent processedEvent = processedEventRepository
                        .findById(bookingCancelledEvent.eventId())
                        .orElseThrow();

        assertThat(processedEvent.getEventId())
                .isEqualTo(bookingCancelledEvent.eventId());

        assertThat(processedEvent.getEventType())
                .isEqualTo("BookingCancelled");

        assertThat(processedEvent.getProcessedAt())
                .isNotNull();

        // duplicate Kafka delivery must not trigger the notification twice
        verify(notificationService, times(1))
                .handleBookingCancelled(bookingCancelledEvent);
    }

    @Test
    void handleBookingCancelled_shouldRollbackProcessedEvent_whenNotificationHandlingFails() {
        BookingCancelledEvent bookingCancelledEvent = createCancelledEvent();

        doThrow(new IllegalStateException("Notification failed"))
                .when(notificationService)
                .handleBookingCancelled(bookingCancelledEvent);

        assertThatThrownBy(() -> bookingEventListener.handleBookingCancelled(bookingCancelledEvent))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Notification failed");

        /*
         * The processed event insert belongs to the same transaction
         * so it must be rolled back when notification handling fails
         */
        assertThat(processedEventRepository.existsById(bookingCancelledEvent.eventId())).isFalse();
    }

    private BookingCancelledEvent createCancelledEvent() {
        return new BookingCancelledEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "guest@example.com",
                UUID.randomUUID(),
                NOW);
    }
}