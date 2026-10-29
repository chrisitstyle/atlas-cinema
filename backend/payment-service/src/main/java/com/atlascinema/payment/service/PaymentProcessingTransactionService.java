
package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.PaymentNotFoundException;
import com.atlascinema.payment.exception.PaymentNotPendingException;
import com.atlascinema.payment.provider.PaymentProviderOutcome;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.repository.PaymentRepository;
import com.atlascinema.payment.service.dto.PaymentChargeDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/*
 * Manages short database transactions for payment processing
 * Protects payment status changes from concurrent execution
 */
@Service
@RequiredArgsConstructor
public class PaymentProcessingTransactionService {

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    /*
     * Atomically claims a pending payment for processing
     * The transaction commits before the provider is called
     */
    @Transactional
    public PaymentChargeDetails startProcessing(UUID paymentId) {
        Objects.requireNonNull(paymentId, "Payment id is required");

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentNotPendingException(
                    paymentId,
                    payment.getStatus());
        }

        payment.markAsProcessing(clock.instant());

        return new PaymentChargeDetails(
                payment.getId(),
                payment.getAmount(),
                payment.getCurrencyCode());
    }

    /*
     * Saves the provider result or reuses an identical completed result
     * The row lock prevents conflicting concurrent finalizations
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

        // the same provider result may arrive from processing and reconciliation
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

        if (providerResult.outcome() == PaymentProviderOutcome.APPROVED) {
            payment.markAsSucceeded(
                    providerResult.providerTransactionId(),
                    clock.instant());
        } else {
            payment.markAsFailed(providerResult.providerTransactionId(), clock.instant());
        }

        return payment;
    }
}
