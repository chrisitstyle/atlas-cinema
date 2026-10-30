package com.atlascinema.booking.payment.service;

import com.atlascinema.booking.payment.event.PaymentResultEvent;
import com.atlascinema.booking.payment.repository.PaymentInboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;

/*
 * Validates and durably records payment results received from Kafka
 * Repeated identical events are accepted without creating duplicates
 */
@Service
@RequiredArgsConstructor
public class PaymentInboxService {

    private final PaymentInboxRepository paymentInboxRepository;
    private final Clock clock;

    @Transactional
    public boolean recordPaymentResult(
            PaymentResultEvent paymentResultEvent,
            String eventPayloadJson) {
        validatePaymentResult(paymentResultEvent);

        if (eventPayloadJson == null || eventPayloadJson.isBlank()) {
            throw new IllegalArgumentException("Payment event payload must not be blank");
        }

        boolean inserted = paymentInboxRepository.insertIfAbsent(
                paymentResultEvent,
                eventPayloadJson,
                clock.instant());

        if (inserted) {
            return true;
        }

        String existingPayload = paymentInboxRepository
                .findPayloadByEventId(paymentResultEvent.eventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Existing payment event was not found"));

        // an event id must always identify the same exact message
        if (!existingPayload.equals(eventPayloadJson)) {
            throw new IllegalStateException(
                    "Payment event %s has conflicting payload data"
                            .formatted(paymentResultEvent.eventId()));
        }

        return false;
    }

    private void validatePaymentResult(PaymentResultEvent paymentResultEvent) {
        Objects.requireNonNull(
                paymentResultEvent,
                "Payment result event is required");

        Objects.requireNonNull(
                paymentResultEvent.eventId(),
                "Event id is required");

        Objects.requireNonNull(
                paymentResultEvent.paymentId(),
                "Payment id is required");

        Objects.requireNonNull(
                paymentResultEvent.bookingId(),
                "Booking id is required");

        Objects.requireNonNull(
                paymentResultEvent.eventType(),
                "Payment event type is required");

        Objects.requireNonNull(
                paymentResultEvent.occurredAt(),
                "Event occurrence time is required");

        if (paymentResultEvent.amount() == null
                || paymentResultEvent.amount().signum() <= 0
                || paymentResultEvent.amount().scale() > 2) {
            throw new IllegalArgumentException("Payment event amount is invalid");
        }

        if (!"PLN".equals(paymentResultEvent.currencyCode())) {
            throw new IllegalArgumentException("Payment event currency is unsupported");
        }

        String providerTransactionId = paymentResultEvent.providerTransactionId();

        if (providerTransactionId == null
                || providerTransactionId.isBlank()) {
            throw new IllegalArgumentException("Provider transaction id is required");
        }
    }
}
