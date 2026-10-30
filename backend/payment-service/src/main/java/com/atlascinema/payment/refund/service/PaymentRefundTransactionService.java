
package com.atlascinema.payment.refund.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.PaymentNotFoundException;
import com.atlascinema.payment.refund.event.BookingEventEnvelope;
import com.atlascinema.payment.refund.event.PaymentRefundRequestedPayload;
import com.atlascinema.payment.refund.provider.PaymentRefundResult;
import com.atlascinema.payment.refund.repository.PaymentRefundInboxRepository;
import com.atlascinema.payment.refund.service.dto.PaymentRefundDetails;
import com.atlascinema.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/*
 * Manages the database transactions surrounding provider refunds
 * Locks inbox and payment records to prevent conflicting state changes
 */
@Service
@RequiredArgsConstructor
public class PaymentRefundTransactionService {

    private static final String REFUND_EVENT_TYPE = "PaymentRefundRequested";

    private final PaymentRefundInboxRepository refundInboxRepository;
    private final PaymentRepository paymentRepository;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    @Transactional
    public Optional<PaymentRefundDetails> startRefund(UUID eventId) {
        PaymentRefundInboxRepository.LockedRefundInboxEvent inboxEvent =
                lockInboxEvent(eventId);

        if (inboxEvent.processed()) {
            return Optional.empty();
        }

        RefundRequest refundRequest = readRefundRequest(inboxEvent);

        Payment payment = lockAndValidatePayment(refundRequest);

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            // different event may request a refund already completed
            refundInboxRepository.markAsProcessed(eventId, clock.instant());
            return Optional.empty();
        }

        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            payment.requestRefund(clock.instant());

        } else if (payment.getStatus() != PaymentStatus.REFUND_PENDING) {
            throw new IllegalStateException(
                    "Payment %s cannot be refunded from status %s"
                            .formatted(payment.getId(), payment.getStatus()));
        }

        return Optional.of(new PaymentRefundDetails(
                payment.getId(),
                payment.getProviderTransactionId(),
                payment.getAmount(),
                payment.getCurrencyCode()
        ));
    }

    @Transactional
    public void completeRefund(
            UUID eventId,
            PaymentRefundResult providerResult
    ) {
        Objects.requireNonNull(providerResult, "Refund result is required");

        PaymentRefundInboxRepository.LockedRefundInboxEvent inboxEvent =
                lockInboxEvent(eventId);

        RefundRequest refundRequest = readRefundRequest(inboxEvent);

        Payment payment = lockAndValidatePayment(refundRequest);

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            if (!Objects.equals(
                    payment.getProviderRefundTransactionId(),
                    providerResult.providerRefundTransactionId()
            )) {
                throw new IllegalStateException(
                        "Payment %s was refunded using a different provider transaction"
                                .formatted(payment.getId()));
            }

        } else if (payment.getStatus() == PaymentStatus.REFUND_PENDING) {
            payment.markAsRefunded(
                    providerResult.providerRefundTransactionId(),
                    clock.instant());

        } else {
            throw new IllegalStateException("Payment %s cannot complete refund from status %s"
                            .formatted(payment.getId(), payment.getStatus()));
        }

        if (!inboxEvent.processed()) {
            // payment and inbox completion commit or roll back together
            refundInboxRepository.markAsProcessed(eventId, clock.instant());
        }
    }

    private PaymentRefundInboxRepository.LockedRefundInboxEvent lockInboxEvent(
            UUID eventId
    ) {
        Objects.requireNonNull(eventId, "Refund event id is required");

        return refundInboxRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalStateException(
                        "Refund inbox event %s was not found".formatted(eventId)));
    }

    private RefundRequest readRefundRequest(
            PaymentRefundInboxRepository.LockedRefundInboxEvent inboxEvent
    ) {
        BookingEventEnvelope bookingEvent = jsonMapper.readValue(
                inboxEvent.payload(),
                BookingEventEnvelope.class);

        if (bookingEvent == null
                || !inboxEvent.eventId().equals(bookingEvent.eventId())
                || !REFUND_EVENT_TYPE.equals(bookingEvent.eventType())
                || bookingEvent.bookingId() == null
                || bookingEvent.payload() == null
                || bookingEvent.payload().isNull()) {
            throw new IllegalStateException("Refund inbox event has inconsistent envelope data");
        }

        PaymentRefundRequestedPayload refundPayload = jsonMapper.treeToValue(
                        bookingEvent.payload(),
                        PaymentRefundRequestedPayload.class);

        if (refundPayload == null
                || refundPayload.paymentId() == null
                || refundPayload.providerTransactionId() == null
                || refundPayload.providerTransactionId().isBlank()) {
            throw new IllegalStateException("Refund inbox event has inconsistent payment details");
        }

        return new RefundRequest(bookingEvent, refundPayload);
    }

    private Payment lockAndValidatePayment(RefundRequest refundRequest) {
        UUID paymentId = refundRequest.refundPayload().paymentId();

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (!payment.getBookingId().equals(
                refundRequest.bookingEvent().bookingId()
        )) {
            throw new IllegalStateException("Refund request booking does not match payment booking");
        }

        if (!Objects.equals(
                payment.getProviderTransactionId(),
                refundRequest.refundPayload().providerTransactionId()
        )) {
            throw new IllegalStateException("Refund request provider transaction does not match payment");
        }

        return payment;
    }

    private record RefundRequest(
            BookingEventEnvelope bookingEvent,
            PaymentRefundRequestedPayload refundPayload
    ) {
    }
}
