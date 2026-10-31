
package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.PaymentNotFoundException;
import com.atlascinema.payment.provider.PaymentProvider;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

/*
 * Recovers payments interrupted during provider processing
 * Retries missing charges using the same provider idempotency key
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentProcessingTransactionService
            paymentProcessingTransactionService;

    public Payment reconcilePayment(UUID paymentId) {
        Objects.requireNonNull(paymentId, "Payment id is required");

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // only interrupted PROCESSING payments need recovery
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            return payment;
        }

        // First check whether the provider already completed the charge
        PaymentProviderResult providerResult =
                paymentProvider.findChargeResult(paymentId)
                        .orElseGet(() -> {
                            // the same payment id is reused for every retry
                            // The provider must guarantee idempotent charges
                            return paymentProvider.charge(
                                    payment.getId(),
                                    payment.getAmount(),
                                    payment.getCurrencyCode());
                        });

        // store the final result and the outbox event atomically
        return paymentProcessingTransactionService.completeProcessing(paymentId, providerResult);
    }
}
