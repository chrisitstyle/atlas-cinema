
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentOutboxEvent;
import com.atlascinema.payment.domain.PaymentOutboxEventType;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.event.PaymentResultEventPayload;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.repository.PaymentOutboxRepository;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.PaymentProcessingTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/*
 * Verifies atomic payment and outbox persistence using real PostgreSQL
 * Tests event content, idempotency, and transaction rollback
 */
class PaymentOutboxIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentOutboxRepository paymentOutboxRepository;

    @Autowired
    private PaymentProcessingTransactionService paymentProcessingTransactionService;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private Clock clock;

    @Test
    void completeProcessing_shouldPersistSucceededEvent_whenProviderApproves() {
        Payment processingPayment = persistProcessingPayment();
        UUID paymentId = processingPayment.getId();

        PaymentProviderResult providerResult = new PaymentProviderResult(
                PaymentProviderOutcome.APPROVED,
                "SIM-" + paymentId);

        paymentProcessingTransactionService.completeProcessing(paymentId, providerResult);

        Payment persistedPayment = paymentRepository.findById(paymentId)
                .orElseThrow();

        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAllByPaymentId(paymentId);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(outboxEvents).hasSize(1);

        PaymentOutboxEvent outboxEvent = outboxEvents.getFirst();

        assertThat(outboxEvent.getEventType()).isEqualTo(PaymentOutboxEventType.PAYMENT_SUCCEEDED);

        assertThat(outboxEvent.getPublishedAt()).isNull();

        assertThat(outboxEvent.getOccurredAt()).isEqualTo(persistedPayment.getUpdatedAt());

        PaymentResultEventPayload eventPayload = jsonMapper.readValue(
                outboxEvent.getPayload(),
                PaymentResultEventPayload.class);

        assertThat(eventPayload.eventId()).isEqualTo(outboxEvent.getId());

        assertThat(eventPayload.eventType()).isEqualTo(PaymentOutboxEventType.PAYMENT_SUCCEEDED);

        assertThat(eventPayload.paymentId()).isEqualTo(paymentId);

        assertThat(eventPayload.bookingId()).isEqualTo(persistedPayment.getBookingId());

        assertThat(eventPayload.amount()).isEqualByComparingTo(PAYMENT_AMOUNT);

        assertThat(eventPayload.currencyCode()).isEqualTo(CURRENCY_CODE);

        assertThat(eventPayload.providerTransactionId()).isEqualTo("SIM-" + paymentId);
    }

    @Test
    void completeProcessing_shouldPersistFailedEvent_whenProviderDeclines() {
        Payment processingPayment = persistProcessingPayment();
        UUID paymentId = processingPayment.getId();

        paymentProcessingTransactionService.completeProcessing(
                paymentId,
                new PaymentProviderResult(
                        PaymentProviderOutcome.DECLINED,
                        "SIM-" + paymentId));

        Payment persistedPayment = paymentRepository.findById(paymentId).orElseThrow();

        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAllByPaymentId(paymentId);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(outboxEvents).hasSize(1);

        assertThat(outboxEvents.getFirst().getEventType()).isEqualTo(PaymentOutboxEventType.PAYMENT_FAILED);
    }

    @Test
    void completeProcessing_shouldNotCreateDuplicateEvent_whenResultIsRepeated() {
        Payment processingPayment = persistProcessingPayment();
        UUID paymentId = processingPayment.getId();

        PaymentProviderResult providerResult = new PaymentProviderResult(
                PaymentProviderOutcome.APPROVED,
                "SIM-" + paymentId);

        paymentProcessingTransactionService.completeProcessing(paymentId, providerResult);

        PaymentOutboxEvent firstOutboxEvent = paymentOutboxRepository.findAllByPaymentId(paymentId)
                        .getFirst();

        paymentProcessingTransactionService.completeProcessing(paymentId, providerResult);

        List<PaymentOutboxEvent> outboxEvents = paymentOutboxRepository.findAllByPaymentId(paymentId);

        assertThat(outboxEvents).hasSize(1);

        assertThat(outboxEvents.getFirst().getId()).isEqualTo(firstOutboxEvent.getId());
    }

    @Test
    void completeProcessing_shouldRollbackPaymentStatus_whenOutboxInsertFails() {
        Payment processingPayment = persistProcessingPayment();
        UUID paymentId = processingPayment.getId();

        // create a conflicting event to force a database constraint violation
        PaymentOutboxEvent conflictingEvent = PaymentOutboxEvent.create(
                UUID.randomUUID(),
                paymentId,
                PaymentOutboxEventType.PAYMENT_SUCCEEDED,
                "{}",
                clock.instant());

        paymentOutboxRepository.saveAndFlush(conflictingEvent);

        assertThatThrownBy(() -> paymentProcessingTransactionService.completeProcessing(
                        paymentId,
                        new PaymentProviderResult(
                                PaymentProviderOutcome.APPROVED,
                                "SIM-" + paymentId))
        ).isInstanceOf(DataIntegrityViolationException.class);

        Payment persistedPayment = paymentRepository.findById(paymentId)
                .orElseThrow();

        // the payment update must roll back together with the failed event insert
        assertThat(persistedPayment.getStatus())
                .isEqualTo(PaymentStatus.PROCESSING);

        assertThat(persistedPayment.getProviderTransactionId()).isNull();

        assertThat(paymentOutboxRepository.findAllByPaymentId(paymentId)).hasSize(1);
    }

    private Payment persistProcessingPayment() {
        Instant processingStartedAt = clock.instant();

        Payment payment = Payment.initiateForBooking(
                UUID.randomUUID(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                "payment-outbox-" + UUID.randomUUID(),
                processingStartedAt.minusSeconds(60));

        payment.markAsProcessing(processingStartedAt);

        return paymentRepository.saveAndFlush(payment);
    }
}
