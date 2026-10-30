
package com.atlascinema.payment.refund.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.event.PaymentRefundedEventPayload;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

/*
 * Stores completed refund events through the transactional outbox
 * Must be invoked inside the transaction that completes the refund
 */
@Component
@RequiredArgsConstructor
public class PaymentRefundedOutboxWriter {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final JsonMapper jsonMapper;

    public void saveRefunded(Payment payment, Instant occurredAt) {
        if (payment.getStatus() != PaymentStatus.REFUNDED) {
            throw new IllegalStateException(
                    "Cannot publish refund completion for payment %s in status %s"
                            .formatted(payment.getId(), payment.getStatus()));
        }

        if (payment.getProviderRefundTransactionId() == null
                || payment.getProviderRefundTransactionId().isBlank()) {
            throw new IllegalStateException("Refund provider reference is required");
        }

        UUID eventId = UUID.randomUUID();

        PaymentRefundedEventPayload payload = new PaymentRefundedEventPayload(
                        eventId,
                        PaymentOutboxEventType.PAYMENT_REFUNDED,
                        payment.getId(),
                        payment.getBookingId(),
                        payment.getAmount(),
                        payment.getCurrencyCode(),
                        payment.getProviderTransactionId(),
                        payment.getProviderRefundTransactionId(),
                        occurredAt);

        String payloadJson = jsonMapper.writeValueAsString(payload);

        PaymentOutboxEvent outboxEvent = PaymentOutboxEvent.create(
                eventId,
                payment.getId(),
                PaymentOutboxEventType.PAYMENT_REFUNDED,
                payloadJson,
                occurredAt);

        paymentOutboxRepository.save(outboxEvent);
    }
}
