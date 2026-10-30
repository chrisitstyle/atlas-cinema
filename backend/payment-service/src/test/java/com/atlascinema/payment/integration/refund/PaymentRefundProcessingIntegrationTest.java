
package com.atlascinema.payment.integration.refund;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.event.PaymentRefundedEventPayload;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.provider.SimulatedPaymentProvider;
import com.atlascinema.payment.refund.event.BookingEventEnvelope;
import com.atlascinema.payment.refund.event.PaymentRefundRequestedPayload;
import com.atlascinema.payment.refund.provider.PaymentRefundProvider;
import com.atlascinema.payment.refund.repository.SimulatedProviderRefundRepository;
import com.atlascinema.payment.refund.service.PaymentRefundInboxService;
import com.atlascinema.payment.refund.service.PaymentRefundProcessingService;
import com.atlascinema.payment.refund.service.PaymentRefundTransactionService;
import com.atlascinema.payment.refund.service.dto.PaymentRefundDetails;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.PaymentProcessingTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies idempotent refund processing using real PostgreSQL
 * Simulates crashes between provider refunds and payment completion
 */
class PaymentRefundProcessingIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    private static final Instant EVENT_TIME = Instant.parse("2026-10-30T12:00:00Z");

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentProcessingTransactionService paymentProcessingTransactionService;

    @Autowired
    private SimulatedPaymentProvider simulatedPaymentProvider;

    @Autowired
    private PaymentRefundInboxService refundInboxService;

    @Autowired
    private PaymentRefundTransactionService refundTransactionService;

    @Autowired
    private PaymentRefundProcessingService refundProcessingService;

    @Autowired
    private PaymentRefundProvider refundProvider;

    @Autowired
    private SimulatedProviderRefundRepository refundRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Test
    void processRefundRequest_shouldRefundPayment_whenChargeSucceeded() {
        Payment payment = createSucceededPayment();

        UUID eventId = recordRefundRequest(
                payment.getBookingId(),
                payment.getId(),
                payment.getProviderTransactionId());

        boolean processed = refundProcessingService
                .processRefundRequest(eventId);

        Payment persistedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertThat(processed).isTrue();

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        assertThat(persistedPayment.getProviderRefundTransactionId()).isEqualTo("SIM-REF-" + payment.getId());

        assertThat(refundRepository.findByPaymentId(payment.getId())).isPresent();

        assertThat(countProviderRefunds(payment.getId())).isEqualTo(1);

        assertThat(isInboxEventProcessed(eventId)).isTrue();
    }

    @Test
    void processRefundRequest_shouldNotRefundTwice_whenEventIsProcessedAgain() {
        Payment payment = createSucceededPayment();

        UUID eventId = recordRefundRequest(
                payment.getBookingId(),
                payment.getId(),
                payment.getProviderTransactionId());

        boolean firstResult = refundProcessingService.processRefundRequest(eventId);

        boolean secondResult = refundProcessingService.processRefundRequest(eventId);

        assertThat(firstResult).isTrue();
        assertThat(secondResult).isFalse();

        assertThat(countProviderRefunds(payment.getId())).isEqualTo(1);

        assertThat(isInboxEventProcessed(eventId)).isTrue();
    }

    @Test
    void processRefundRequest_shouldRecoverRefund_whenAppCrashesAfterProviderCall() {
        Payment payment = createSucceededPayment();

        UUID eventId = recordRefundRequest(
                payment.getBookingId(),
                payment.getId(),
                payment.getProviderTransactionId());

        // transaction A commits REFUND_PENDING
        PaymentRefundDetails refundDetails = refundTransactionService
                .startRefund(eventId)
                .orElseThrow();

        // the provider completes the refund in its own transaction
        refundProvider.refund(
                refundDetails.paymentId(),
                refundDetails.providerTransactionId(),
                refundDetails.amount(),
                refundDetails.currencyCode());

        // simulate a crash before completeRefund() is called
        Payment paymentBeforeRecovery = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertThat(paymentBeforeRecovery.getStatus())
                .isEqualTo(PaymentStatus.REFUND_PENDING);

        assertThat(isInboxEventProcessed(eventId)).isFalse();

        assertThat(countProviderRefunds(payment.getId())).isEqualTo(1);

        // a later scheduler attempt finds the existing provider refund
        boolean recovered = refundProcessingService.processRefundRequest(eventId);

        Payment paymentAfterRecovery = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        assertThat(recovered).isTrue();

        assertThat(paymentAfterRecovery.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        assertThat(paymentAfterRecovery.getProviderRefundTransactionId())
                .isEqualTo("SIM-REF-" + payment.getId());

        assertThat(countProviderRefunds(payment.getId())).isEqualTo(1);

        assertThat(isInboxEventProcessed(eventId)).isTrue();
    }

    @Test
    void processRefundRequest_shouldNotRefundTwice_whenDifferentEventsReferToSamePayment() {
        Payment payment = createSucceededPayment();

        UUID firstEventId = recordRefundRequest(
                payment.getBookingId(),
                payment.getId(),
                payment.getProviderTransactionId());

        UUID secondEventId = recordRefundRequest(
                payment.getBookingId(),
                payment.getId(),
                payment.getProviderTransactionId());

        assertThat(firstEventId).isNotEqualTo(secondEventId);

        boolean firstResult = refundProcessingService.processRefundRequest(firstEventId);

        boolean secondResult = refundProcessingService.processRefundRequest(secondEventId);

        assertThat(firstResult).isTrue();

        // the payment is already REFUNDED, so no second provider call is needed
        assertThat(secondResult).isFalse();

        assertThat(paymentRepository
                .findById(payment.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(PaymentStatus.REFUNDED);

        assertThat(countProviderRefunds(payment.getId()))
                .isEqualTo(1);

        assertThat(isInboxEventProcessed(firstEventId)).isTrue();
        assertThat(isInboxEventProcessed(secondEventId)).isTrue();
    }

    @Test
    void processRefundRequest_shouldRejectEvent_whenBookingDoesNotMatchPayment() {
        Payment payment = createSucceededPayment();

        UUID unrelatedBookingId = UUID.randomUUID();

        UUID eventId = recordRefundRequest(
                unrelatedBookingId,
                payment.getId(),
                payment.getProviderTransactionId());

        assertThatThrownBy(() -> refundProcessingService.processRefundRequest(eventId)
        ).isInstanceOf(IllegalStateException.class);

        assertThat(paymentRepository
                .findById(payment.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(countProviderRefunds(payment.getId())).isZero();

        assertThat(isInboxEventProcessed(eventId)).isFalse();
    }

    @Test
    void processRefundRequest_shouldRejectEvent_whenProviderTransactionDoesNotMatch() {
        Payment payment = createSucceededPayment();

        UUID eventId = recordRefundRequest(
                payment.getBookingId(),
                payment.getId(),
                "SIM-WRONG-TRANSACTION");

        assertThatThrownBy(() -> refundProcessingService.processRefundRequest(eventId)
        ).isInstanceOf(IllegalStateException.class);

        assertThat(paymentRepository
                .findById(payment.getId())
                .orElseThrow()
                .getStatus())
                .isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(countProviderRefunds(payment.getId())).isZero();

        assertThat(isInboxEventProcessed(eventId)).isFalse();
    }

    @Test
    void processRefundRequest_shouldCreateRefundedOutboxEvent() {
        Payment payment = createSucceededPayment();

        UUID refundRequestEventId = recordRefundRequest(
                payment.getBookingId(),
                payment.getId(),
                payment.getProviderTransactionId());

        refundProcessingService.processRefundRequest(refundRequestEventId);

        Payment refundedPayment = paymentRepository
                .findById(payment.getId())
                .orElseThrow();

        List<PaymentOutboxEvent> refundEvents =
                paymentOutboxRepository.findAllByPaymentId(payment.getId())
                        .stream()
                        .filter(event ->
                                event.getEventType() == PaymentOutboxEventType.PAYMENT_REFUNDED)
                        .toList();

        assertThat(refundedPayment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        assertThat(isInboxEventProcessed(refundRequestEventId)).isTrue();

        assertThat(refundEvents).hasSize(1);

        PaymentOutboxEvent refundEvent = refundEvents.getFirst();

        assertThat(refundEvent.getPublishedAt()).isNull();

        PaymentRefundedEventPayload eventPayload = jsonMapper.readValue(
                refundEvent.getPayload(),
                PaymentRefundedEventPayload.class);

        assertThat(eventPayload.eventId()).isEqualTo(refundEvent.getId());

        assertThat(eventPayload.eventType()).isEqualTo(PaymentOutboxEventType.PAYMENT_REFUNDED);

        assertThat(eventPayload.paymentId()).isEqualTo(payment.getId());

        assertThat(eventPayload.bookingId()).isEqualTo(payment.getBookingId());

        assertThat(eventPayload.amount()).isEqualByComparingTo(PAYMENT_AMOUNT);

        assertThat(eventPayload.currencyCode()).isEqualTo(CURRENCY_CODE);

        assertThat(eventPayload.providerTransactionId()).isEqualTo(payment.getProviderTransactionId());

        assertThat(eventPayload.providerRefundTransactionId())
                .isEqualTo(refundedPayment.getProviderRefundTransactionId());

        assertThat(eventPayload.occurredAt()).isNotNull();
    }

    @Test
    void processRefundRequest_shouldCreateOnlyOneRefundedEvent_forMultipleRequests() {
        Payment payment = createSucceededPayment();

        UUID firstEventId = recordRefundRequest(
                payment.getBookingId(),
                payment.getId(),
                payment.getProviderTransactionId());

        UUID secondEventId = recordRefundRequest(
                payment.getBookingId(),
                payment.getId(),
                payment.getProviderTransactionId());

        refundProcessingService.processRefundRequest(firstEventId);
        refundProcessingService.processRefundRequest(secondEventId);

        List<PaymentOutboxEvent> refundEvents = paymentOutboxRepository.findAllByPaymentId(payment.getId())
                .stream()
                .filter(event ->
                        event.getEventType() == PaymentOutboxEventType.PAYMENT_REFUNDED
                ).toList();

        assertThat(refundEvents).hasSize(1);

        assertThat(countProviderRefunds(payment.getId())).isEqualTo(1);

        assertThat(isInboxEventProcessed(firstEventId)).isTrue();

        assertThat(isInboxEventProcessed(secondEventId)).isTrue();
    }

    private Payment createSucceededPayment() {
        Payment pendingPayment = Payment.initiateForBooking(
                UUID.randomUUID(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                "refund-integration-" + UUID.randomUUID(),
                EVENT_TIME.minusSeconds(60));

        paymentRepository.saveAndFlush(pendingPayment);

        UUID paymentId = pendingPayment.getId();

        paymentProcessingTransactionService.startProcessing(paymentId);

        PaymentProviderResult providerResult = simulatedPaymentProvider.charge(
                paymentId,
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        paymentProcessingTransactionService.completeProcessing(
                paymentId,
                providerResult);

        return paymentRepository.findById(paymentId).orElseThrow();
    }

    private UUID recordRefundRequest(
            UUID bookingId,
            UUID paymentId,
            String providerTransactionId
    ) {
        PaymentRefundRequestedPayload refundPayload = new PaymentRefundRequestedPayload(
                paymentId,
                providerTransactionId,
                "BOOKING_NOT_CONFIRMABLE");

        BookingEventEnvelope bookingEvent = new BookingEventEnvelope(
                UUID.randomUUID(),
                "PaymentRefundRequested",
                bookingId,
                EVENT_TIME,
                jsonMapper.valueToTree(refundPayload));

        refundInboxService.recordRefundRequest(
                bookingEvent,
                refundPayload,
                jsonMapper.writeValueAsString(bookingEvent));

        return bookingEvent.eventId();
    }

    private long countProviderRefunds(UUID paymentId) {
        Long count = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                        FROM simulated_provider_refunds
                        WHERE payment_id = ?
                        """,
                Long.class,
                paymentId
        );

        return count;
    }

    private boolean isInboxEventProcessed(UUID eventId) {
        Boolean processed = jdbcTemplate.queryForObject(
                """
                        SELECT processed_at IS NOT NULL
                        FROM payment_refund_inbox_events
                        WHERE event_id = ?
                        """,
                Boolean.class,
                eventId
        );

        return Boolean.TRUE.equals(processed);
    }
}
