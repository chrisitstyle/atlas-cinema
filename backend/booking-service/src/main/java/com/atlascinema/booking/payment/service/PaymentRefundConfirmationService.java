
package com.atlascinema.booking.payment.service;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.outbox.OutboxEventRepository;
import com.atlascinema.booking.payment.event.PaymentRefundedEvent;
import com.atlascinema.booking.payment.repository.PaymentRefundConfirmationRepository;
import com.atlascinema.booking.payment.repository.PaymentRefundConfirmationRepository.StoredRefundConfirmation;
import com.atlascinema.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

/*
 * Verifies completed refunds against bookings and local refund requests
 * Stores confirmed compensation outcomes idempotently
 */
@Service
@RequiredArgsConstructor
public class PaymentRefundConfirmationService {

    private static final String REFUNDED_EVENT_TYPE = "PAYMENT_REFUNDED";
    private static final String SUPPORTED_CURRENCY = "PLN";

    private final BookingRepository bookingRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentRefundConfirmationRepository confirmationRepository;
    private final Clock clock;

    @Transactional
    public boolean recordRefundConfirmation(
            PaymentRefundedEvent event,
            String eventPayloadJson
    ) {
        validateEvent(event, eventPayloadJson);

        Booking booking = bookingRepository.findById(event.bookingId())
                .orElseThrow(() -> new IllegalStateException(
                        "Booking %s was not found for refund confirmation"
                                .formatted(event.bookingId())));

        if (booking.getTotalPrice().compareTo(event.amount()) != 0) {
            throw new IllegalStateException(
                    "Refund amount does not match booking %s"
                            .formatted(booking.getId()));
        }

        boolean refundWasRequested = outboxEventRepository.existsMatchingRefundRequest(
                        event.bookingId(),
                        event.paymentId(),
                        event.providerTransactionId());

        if (!refundWasRequested) {
            throw new IllegalStateException(
                    "No matching refund request exists for payment %s"
                            .formatted(event.paymentId()));
        }

        boolean inserted = confirmationRepository.insertIfAbsent(
                event,
                eventPayloadJson,
                clock.instant());

        if (inserted) {
            return true;
        }

        List<StoredRefundConfirmation> existingConfirmations = confirmationRepository.findConflictingConfirmations(
                        event.eventId(),
                        event.paymentId());

        if (existingConfirmations.size() != 1) {
            throw new IllegalStateException("Refund confirmation has conflicting identifiers");
        }

        StoredRefundConfirmation existing = existingConfirmations.getFirst();

        if (existing.eventId().equals(event.eventId())) {
            // the same event id must always carry the same JSON message
            if (!existing.payload().equals(eventPayloadJson)) {
                throw new IllegalStateException(
                        "Refund event %s has conflicting payload"
                                .formatted(event.eventId())
                );
            }

            return false;
        }

        // A different event id may report the same completed refund
        // Accept only when all business identifiers and values match
        boolean sameRefund =
                existing.paymentId().equals(event.paymentId())
                        && existing.bookingId().equals(event.bookingId())
                        && existing.providerTransactionId().equals(
                        event.providerTransactionId()
                )
                        && existing.providerRefundTransactionId().equals(
                        event.providerRefundTransactionId()
                )
                        && existing.amount().compareTo(event.amount()) == 0
                        && existing.currencyCode().equals(event.currencyCode());

        if (!sameRefund) {
            throw new IllegalStateException(
                    "Payment %s has a conflicting refund confirmation"
                            .formatted(event.paymentId()));
        }

        return false;
    }

    private void validateEvent(
            PaymentRefundedEvent event,
            String eventPayloadJson
    ) {
        Objects.requireNonNull(event, "Refund event is required");
        Objects.requireNonNull(event.eventId(), "Event id is required");
        Objects.requireNonNull(event.paymentId(), "Payment id is required");
        Objects.requireNonNull(event.bookingId(), "Booking id is required");
        Objects.requireNonNull(event.occurredAt(), "Event time is required");

        if (!REFUNDED_EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException("Unexpected refund event type");
        }

        if (event.amount() == null
                || event.amount().signum() <= 0
                || event.amount().scale() > 2) {
            throw new IllegalArgumentException("Invalid refunded amount");
        }

        if (!SUPPORTED_CURRENCY.equals(event.currencyCode())) {
            throw new IllegalArgumentException("Unsupported refund currency");
        }

        validateReference(
                event.providerTransactionId(),
                "Provider transaction id");

        validateReference(
                event.providerRefundTransactionId(),
                "Provider refund transaction id");

        if (eventPayloadJson == null || eventPayloadJson.isBlank()) {
            throw new IllegalArgumentException("Refund event JSON must not be blank");
        }
    }

    private void validateReference(String value, String fieldName) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(fieldName + " must contain between 1 and 128 characters");
        }
    }
}
