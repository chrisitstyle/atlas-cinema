package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.provider.PaymentProvider;
import com.atlascinema.payment.provider.PaymentProviderResult;
import com.atlascinema.payment.service.dto.PaymentChargeDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/*
 * Coordinates payment processing across database transactions
 * Calls the payment provider only after the processing state is committed
 */
@Service
@RequiredArgsConstructor
public class PaymentProcessingService {

    private final PaymentProcessingTransactionService paymentProcessingTransactionService;
    private final PaymentProvider paymentProvider;

    public Payment processPayment(UUID paymentId) {

        // Transaction A commits the PROCESSING state
        PaymentChargeDetails paymentChargeDetails = paymentProcessingTransactionService.startProcessing(paymentId);

        // the provider call runs without an open payment database transaction
        PaymentProviderResult providerResult = paymentProvider.charge(
                paymentChargeDetails.paymentId(),
                paymentChargeDetails.amount(),
                paymentChargeDetails.currencyCode());

        // Transaction B commits the definitive provider result
        return paymentProcessingTransactionService.completeProcessing(
                paymentId,
                providerResult);
    }
}
