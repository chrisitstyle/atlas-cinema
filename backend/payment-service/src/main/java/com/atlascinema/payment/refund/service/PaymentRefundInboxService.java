
package com.atlascinema.payment.refund.service;

import com.atlascinema.payment.refund.event.BookingEventEnvelope;
import com.atlascinema.payment.refund.event.PaymentRefundRequestedPayload;
import com.atlascinema.payment.refund.repository.PaymentRefundInboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/*
 * Validates and records refund requests durably
 * Repeated identical events are accepted without creating duplicates
 */
@Service
@RequiredArgsConstructor
public class PaymentRefundInboxService {

    private static final String EXPECTED_EVENT_TYPE =
            "PaymentRefundRequested";

    private final PaymentRefundInboxRepository paymentRefundInboxRepository;
    private final Clock clock;

    @Transactional
    public boolean recordRefundRequest(
            BookingEventEnvelope event,
            PaymentRefundRequestedPayload refundRequest,
            String eventPayloadJson
    ) {
        validateEvent(event, refundRequest, eventPayloadJson);

        boolean inserted = paymentRefundInboxRepository.insertIfAbsent(
                event,
                refundRequest,
                eventPayloadJson,
                clock.instant());

        if (inserted) {
            return true;
        }

        String existingPayload = paymentRefundInboxRepository
                .findPayloadByEventId(event.eventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Refund event %s was not found after conflict"
                                .formatted(event.eventId())));

        // reusing one event id for different data is a contract violation
        if (!existingPayload.equals(eventPayloadJson)) {
            throw new IllegalStateException(
                    "Refund event %s has conflicting payload"
                            .formatted(event.eventId()));
        }

        return false;
    }

    private void validateEvent(
            BookingEventEnvelope event,
            PaymentRefundRequestedPayload refundRequest,
            String eventPayloadJson
    ) {
        Objects.requireNonNull(event, "Booking event is required");
        Objects.requireNonNull(refundRequest, "Refund request is required");

        Objects.requireNonNull(event.eventId(), "Event id is required");
        Objects.requireNonNull(event.bookingId(), "Booking id is required");
        Objects.requireNonNull(event.occurredAt(), "Event time is required");
        Objects.requireNonNull(refundRequest.paymentId(), "Payment id is required");

        if (!EXPECTED_EVENT_TYPE.equals(event.eventType())) {
            throw new IllegalArgumentException("Unexpected booking event type");
        }

        String providerTransactionId =
                refundRequest.providerTransactionId();

        if (providerTransactionId == null
                || providerTransactionId.isBlank()
                || providerTransactionId.length() > 128) {
            throw new IllegalArgumentException("Invalid provider transaction id");
        }

        String reason = refundRequest.reason();

        if (reason == null
                || reason.isBlank()
                || reason.length() > 64) {
            throw new IllegalArgumentException("Invalid refund reason");
        }

        if (eventPayloadJson == null || eventPayloadJson.isBlank()) {
            throw new IllegalArgumentException("Refund event JSON must not be blank");
        }
    }
}
