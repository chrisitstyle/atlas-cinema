package com.atlascinema.notification.integration.listener;

import com.atlascinema.notification.event.BookingCancelledEvent;
import com.atlascinema.notification.event.BookingCancelledPayload;
import com.atlascinema.notification.event.BookingEventMessage;
import com.atlascinema.notification.event.BookingEventType;
import com.atlascinema.notification.integration.AbstractIntegrationTest;
import com.atlascinema.notification.listener.BookingEventListener;
import com.atlascinema.notification.processed.ProcessedEvent;
import com.atlascinema.notification.processed.ProcessedEventRepository;
import com.atlascinema.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class BookingEventListenerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Autowired
    private BookingEventListener bookingEventListener;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        processedEventRepository.deleteAll();
    }

    @Test
    void handleBookingEvent_shouldHandleBookingCancelledEventOnlyOnce_whenSameEventIsDeliveredTwice() {
        BookingCancelledEvent bookingCancelledEvent = createBookingCancelledEvent();

        BookingEventMessage bookingEventMessage = createBookingEventMessage(bookingCancelledEvent);

        bookingEventListener.handleBookingEvent(bookingEventMessage);

        bookingEventListener.handleBookingEvent(bookingEventMessage);

        /*
         * The same event id can exist only once
         * in processed_events
         */
        assertThat(processedEventRepository.count()).isEqualTo(1);

        ProcessedEvent processedEvent = processedEventRepository
                        .findById(bookingCancelledEvent.eventId()).orElseThrow();

        assertThat(processedEvent.getEventId()
        ).isEqualTo(bookingCancelledEvent.eventId());

        assertThat(processedEvent.getEventType()).isEqualTo(BookingEventType.BOOKING_CANCELLED.eventName());

        assertThat(processedEvent.getProcessedAt()).isNotNull();

        /*
         * Duplicate Kafka delivery must not trigger
         * the notification twice
         */
        verify(notificationService, times(1)
        ).handleBookingCancelled(bookingCancelledEvent);
    }

    @Test
    void handleBookingEvent_shouldRollbackProcessedEvent_whenBookingCancelledNotificationHandlingFails() {
        BookingCancelledEvent bookingCancelledEvent = createBookingCancelledEvent();

        BookingEventMessage bookingEventMessage = createBookingEventMessage(
                        bookingCancelledEvent);

        doThrow(new IllegalStateException("Notification failed")
        ).when(notificationService)
                .handleBookingCancelled(bookingCancelledEvent);

        assertThatThrownBy(() -> bookingEventListener.handleBookingEvent(
                        bookingEventMessage)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Notification failed");

        /*
         * The processed event insert belongs to the same transaction
         * so it must be rolled back when notification handling fails
         */
        assertThat(processedEventRepository.existsById(bookingCancelledEvent.eventId())).isFalse();
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

    private BookingEventMessage createBookingEventMessage(
            BookingCancelledEvent bookingCancelledEvent
    ) {
        BookingCancelledPayload bookingCancelledPayload = new BookingCancelledPayload(
                        bookingCancelledEvent.customerId(),
                        bookingCancelledEvent.contactEmail(),
                        bookingCancelledEvent.screeningId());

        return new BookingEventMessage(
                bookingCancelledEvent.eventId(),
                BookingEventType.BOOKING_CANCELLED.eventName(),
                bookingCancelledEvent.bookingId(),
                bookingCancelledEvent.occurredAt(),
                jsonMapper.valueToTree(bookingCancelledPayload));
    }
}