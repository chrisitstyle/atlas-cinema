
package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.event.PaymentResultEventPayload;
import com.atlascinema.payment.exception.PaymentNotFoundException;
import com.atlascinema.payment.exception.PaymentNotPendingException;
import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.dto.PaymentChargeDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/*
 * Manages short transactions for payment processing
 * Stores payment results and outbox events atomically
 */
@Service
@RequiredArgsConstructor
public class PaymentProcessingTransactionService {

    private final PaymentRepository paymentRepository;
    private final PaymentOutboxRepository paymentOutboxRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    /*
     * Atomically claims a pending payment before contacting the provider
     */
    @Transactional
    public PaymentChargeDetails startProcessing(UUID paymentId) {
        Objects.requireNonNull(paymentId, "Payment id is required");

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentNotPendingException(paymentId, payment.getStatus());
        }

        payment.markAsProcessing(clock.instant());

        return new PaymentChargeDetails(
                payment.getId(),
                payment.getAmount(),
                payment.getCurrencyCode());
    }

    /*
     * Completes payment processing and creates an outbox event
     * Both changes commit or roll back together
     */
    @Transactional
    public Payment completeProcessing(
            UUID paymentId,
            PaymentProviderResult providerResult
    ) {
        Objects.requireNonNull(paymentId, "Payment id is required");
        Objects.requireNonNull(providerResult, "Provider result is required");
        Objects.requireNonNull(providerResult.outcome(), "Provider outcome is required");

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        PaymentStatus expectedFinalStatus = switch (providerResult.outcome()) {
            case APPROVED -> PaymentStatus.SUCCEEDED;
            case DECLINED -> PaymentStatus.FAILED;
        };

        // repeated identical results must not create duplicate events
        if (payment.getStatus() == expectedFinalStatus) {
            if (!Objects.equals(
                    payment.getProviderTransactionId(),
                    providerResult.providerTransactionId()
            )) {
                throw new IllegalStateException("Payment %s has already been completed with a different provider transaction"
                                .formatted(paymentId));
            }

            return payment;
        }

        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Payment %s cannot complete processing from status %s"
                            .formatted(paymentId, payment.getStatus()));
        }

        Instant completedAt = clock.instant();

        PaymentOutboxEventType eventType;

        if (providerResult.outcome() == PaymentProviderOutcome.APPROVED) {
            payment.markAsSucceeded(providerResult.providerTransactionId(), completedAt);

            eventType = PaymentOutboxEventType.PAYMENT_SUCCEEDED;

        } else {
            payment.markAsFailed(providerResult.providerTransactionId(), completedAt);

            eventType = PaymentOutboxEventType.PAYMENT_FAILED;
        }

        // use one event id for the outbox record and the JSON payload
        UUID eventId = UUID.randomUUID();

        PaymentResultEventPayload paymentResultEventPayload = new PaymentResultEventPayload(
                        eventId,
                        eventType,
                        payment.getId(),
                        payment.getBookingId(),
                        payment.getAmount(),
                        payment.getCurrencyCode(),
                        payment.getProviderTransactionId(),
                        completedAt);

        String eventPayloadJson = jsonMapper.writeValueAsString(paymentResultEventPayload);

        PaymentOutboxEvent paymentOutboxEvent = PaymentOutboxEvent.create(
                eventId,
                payment.getId(),
                eventType,
                eventPayloadJson,
                completedAt);

        paymentOutboxRepository.save(paymentOutboxEvent);

        return payment;
    }
}
