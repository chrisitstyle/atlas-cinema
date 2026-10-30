package com.atlascinema.payment.refund.service;

import com.atlascinema.payment.refund.provider.PaymentRefundProvider;
import com.atlascinema.payment.refund.provider.PaymentRefundResult;
import com.atlascinema.payment.refund.service.dto.PaymentRefundDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/*
 * Coordinates refund processing without holding a database transaction
 * Recovers existing provider refunds before requesting a new one
 */
@Service
@RequiredArgsConstructor
public class PaymentRefundProcessingService {

    private final PaymentRefundTransactionService refundTransactionService;
    private final PaymentRefundProvider refundProvider;

    public boolean processRefundRequest(UUID eventId) {
        Optional<PaymentRefundDetails> refundDetails =
                refundTransactionService.startRefund(eventId);

        if (refundDetails.isEmpty()) {
            return false;
        }

        PaymentRefundDetails details = refundDetails.get();

        // previous attempt may have succeeded before the application crashed
        PaymentRefundResult providerResult = refundProvider
                .findRefundResult(details.paymentId())
                .orElseGet(() -> refundProvider.refund(
                        details.paymentId(),
                        details.providerTransactionId(),
                        details.amount(),
                        details.currencyCode()));

        // the payment and inbox event are finalized in a new transaction
        refundTransactionService.completeRefund(eventId, providerResult);

        return true;
    }
}