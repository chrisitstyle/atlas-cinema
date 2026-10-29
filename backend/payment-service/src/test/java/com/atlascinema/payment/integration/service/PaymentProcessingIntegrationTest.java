
package com.atlascinema.payment.integration.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.PaymentNotFoundException;
import com.atlascinema.payment.exception.PaymentNotPendingException;
import com.atlascinema.payment.integration.AbstractIntegrationTest;
import com.atlascinema.payment.provider.PaymentProvider;
import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.PaymentProcessingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * Verifies payment processing with real PostgreSQL transactions
 * Simulates successful, declined, and interrupted provider operations
 */
class PaymentProcessingIntegrationTest extends AbstractIntegrationTest {

    private static final BigDecimal PAYMENT_AMOUNT = new BigDecimal("29.90");

    private static final String CURRENCY_CODE = "PLN";

    private static final Instant INITIATED_AT = Instant.parse("2026-10-29T10:00:00Z");

    @Autowired
    private PaymentProcessingService paymentProcessingService;

    @Autowired
    private PaymentRepository paymentRepository;

    @MockitoBean
    private PaymentProvider paymentProvider;

    @Test
    void processPayment_shouldPersistSucceededStatus_whenProviderApproves() {
        Payment pendingPayment = persistPendingPayment();

        String providerTransactionId = "SIM-" + pendingPayment.getId();

        when(paymentProvider.charge(
                pendingPayment.getId(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE
        )).thenReturn(new PaymentProviderResult(
                PaymentProviderOutcome.APPROVED,
                providerTransactionId));

        Payment processedPayment = paymentProcessingService.processPayment(pendingPayment.getId());

        Payment persistedPayment = paymentRepository
                .findById(pendingPayment.getId())
                .orElseThrow();

        assertThat(processedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);

        assertThat(persistedPayment.getProviderTransactionId()).isEqualTo(providerTransactionId);

        assertThat(persistedPayment.getUpdatedAt()).isAfter(INITIATED_AT);

        assertThat(paymentRepository.count()).isEqualTo(1);

        verify(paymentProvider).charge(
                pendingPayment.getId(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE);
    }

    @Test
    void processPayment_shouldPersistFailedStatus_whenProviderDeclines() {
        Payment pendingPayment = persistPendingPayment();

        String providerTransactionId = "SIM-" + pendingPayment.getId();

        when(paymentProvider.charge(
                pendingPayment.getId(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE
        )).thenReturn(new PaymentProviderResult(
                PaymentProviderOutcome.DECLINED,
                providerTransactionId));

        Payment processedPayment = paymentProcessingService.processPayment(pendingPayment.getId());

        Payment persistedPayment = paymentRepository
                .findById(pendingPayment.getId())
                .orElseThrow();

        assertThat(processedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(persistedPayment.getStatus()).isEqualTo(PaymentStatus.FAILED);

        assertThat(persistedPayment.getProviderTransactionId()).isEqualTo(providerTransactionId);

        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void processPayment_shouldKeepProcessingStatus_whenProviderThrowsException() {
        Payment pendingPayment = persistPendingPayment();

        when(paymentProvider.charge(
                pendingPayment.getId(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE
        )).thenThrow(new IllegalStateException("Payment provider is unavailable"));

        assertThatThrownBy(() -> paymentProcessingService.processPayment(pendingPayment.getId())
        ).isInstanceOf(IllegalStateException.class)
                .hasMessage("Payment provider is unavailable");

        Payment persistedPayment = paymentRepository
                .findById(pendingPayment.getId())
                .orElseThrow();

        // the provider outcome is unknown, so the payment cannot be failed
        assertThat(persistedPayment.getStatus())
                .isEqualTo(PaymentStatus.PROCESSING);

        assertThat(persistedPayment.getProviderTransactionId()).isNull();

        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void processPayment_shouldRejectRepeatedProcessing_whenPaymentAlreadySucceeded() {
        Payment pendingPayment = persistPendingPayment();

        when(paymentProvider.charge(
                pendingPayment.getId(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE
        )).thenReturn(new PaymentProviderResult(
                PaymentProviderOutcome.APPROVED,
                "SIM-" + pendingPayment.getId()));

        paymentProcessingService.processPayment(pendingPayment.getId());

        assertThatThrownBy(() -> paymentProcessingService.processPayment(pendingPayment.getId())
        ).isInstanceOf(PaymentNotPendingException.class);

        // repeated processing must not charge the customer again
        verify(paymentProvider, times(1)).charge(
                pendingPayment.getId(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE);

        assertThat(paymentRepository.count()).isEqualTo(1);
    }

    @Test
    void processPayment_shouldRejectRequest_whenPaymentDoesNotExist() {
        UUID nonexistentPaymentId = UUID.randomUUID();

        assertThatThrownBy(() -> paymentProcessingService.processPayment(nonexistentPaymentId)
        ).isInstanceOf(PaymentNotFoundException.class);

        assertThat(paymentRepository.count()).isZero();
    }

    private Payment persistPendingPayment() {
        Payment pendingPayment = Payment.initiateForBooking(
                UUID.randomUUID(),
                PAYMENT_AMOUNT,
                CURRENCY_CODE,
                "payment-processing-" + UUID.randomUUID(),
                INITIATED_AT);

        return paymentRepository.saveAndFlush(pendingPayment);
    }
}
