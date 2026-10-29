
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
 * doesnt communicate with the external payment provider
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
     * Persists the definitive provider result in a separate transaction
     */
    @Transactional
    public Payment completeProcessing(
            UUID paymentId,
            PaymentProviderResult providerResult
    ) {
        Objects.requireNonNull(paymentId, "Payment id is required");
        Objects.requireNonNull(providerResult, "Provider result is required");

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (providerResult.outcome() == PaymentProviderOutcome.APPROVED) {
            payment.markAsSucceeded(
                    providerResult.providerTransactionId(),
                    clock.instant());

        } else if (providerResult.outcome() == PaymentProviderOutcome.DECLINED) {
            payment.markAsFailed(
                    providerResult.providerTransactionId(),
                    clock.instant());

        } else {
            throw new IllegalArgumentException("Unsupported payment provider outcome");
        }

        return payment;
    }
}
