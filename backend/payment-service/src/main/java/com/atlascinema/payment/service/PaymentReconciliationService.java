
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
import java.util.Optional;
import java.util.UUID;

/*
 * Recovers payment results that were not persisted after provider processing
 * Never initiates a new charge during reconciliation
 */
@Service
@RequiredArgsConstructor
public class PaymentReconciliationService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final PaymentProcessingTransactionService paymentProcessingTransactionService;

    public Payment reconcilePayment(UUID paymentId) {
        Objects.requireNonNull(paymentId, "Payment id is required");

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        // only payments with an unknown local result need reconciliation
        if (payment.getStatus() != PaymentStatus.PROCESSING) {
            return payment;
        }

        Optional<PaymentProviderResult> providerResult =
                paymentProvider.findChargeResult(paymentId);

        // an unavailable result is not evidence of a declined payment
        if (providerResult.isEmpty()) {
            return payment;
        }

        return paymentProcessingTransactionService.completeProcessing(paymentId, providerResult.get());
    }
}
