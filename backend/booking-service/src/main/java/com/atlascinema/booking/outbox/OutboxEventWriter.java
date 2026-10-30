package com.atlascinema.booking.outbox;

import com.atlascinema.booking.event.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final JsonMapper jsonMapper;

    public void saveCancelled(
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

        saveBookingEventMessage(bookingEventMessage, createdAt);
    }

    public void saveExpired(
            BookingExpiredEvent bookingExpiredEvent,
            LocalDateTime createdAt
    ) {
        BookingExpiredPayload bookingExpiredPayload = new BookingExpiredPayload(
                bookingExpiredEvent.customerId(),
                bookingExpiredEvent.contactEmail(),
                bookingExpiredEvent.screeningId());

        /*
         * Kafka always receives the same outer message structure
         * while payload contains data specific to this event type
         */
        BookingEventMessage bookingEventMessage = new BookingEventMessage(
                bookingExpiredEvent.eventId(),
                BookingEventType.BOOKING_EXPIRED.eventName(),
                bookingExpiredEvent.bookingId(),
                bookingExpiredEvent.occurredAt(),
                jsonMapper.valueToTree(bookingExpiredPayload));

        saveBookingEventMessage(bookingEventMessage, createdAt);
    }

    private void saveBookingEventMessage(
            BookingEventMessage bookingEventMessage,
            LocalDateTime createdAt
    ) {
        /*
         * Store the complete Kafka message now so publishing later
         * does not depend on the current state of the booking
         */
        String serializedBookingEventMessage = jsonMapper.writeValueAsString(
                bookingEventMessage);

        OutboxEvent outboxEvent = OutboxEvent.create(
                bookingEventMessage.eventId(),
                bookingEventMessage.bookingId(),
                bookingEventMessage.eventType(),
                serializedBookingEventMessage,
                createdAt);

        /*
         * PostgreSQL stores the event before a separate publisher
         * sends it to Kafka
         */
        outboxEventRepository.save(outboxEvent);
    }

    // persist a refund request in the same transaction as the inbox decision
    public void savePaymentRefundRequested(
            UUID bookingId,
            UUID paymentId,
            String providerTransactionId,
            Instant occurredAt,
            LocalDateTime createdAt
    ) {
        PaymentRefundRequestedPayload payload = new PaymentRefundRequestedPayload(
                        paymentId,
                        providerTransactionId,
                        "BOOKING_NOT_CONFIRMABLE");

        BookingEventMessage message = new BookingEventMessage(
                UUID.randomUUID(),
                BookingEventType.PAYMENT_REFUND_REQUESTED.eventName(),
                bookingId,
                occurredAt,
                jsonMapper.valueToTree(payload));

        saveBookingEventMessage(message, createdAt);
    }
}