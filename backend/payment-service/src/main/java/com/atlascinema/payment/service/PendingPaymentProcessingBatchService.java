
package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.PaymentStatus;
import com.atlascinema.payment.exception.PaymentNotPendingException;
import com.atlascinema.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/*
 * Processes a bounded batch of newly initiated payments
 * Each payment is claimed and charged by the existing processing service
 */
@Slf4j
@Service
public class PendingPaymentProcessingBatchService {

    private final PaymentRepository paymentRepository;
    private final PaymentProcessingService paymentProcessingService;
    private final int batchSize;

    public PendingPaymentProcessingBatchService(
            PaymentRepository paymentRepository,
            PaymentProcessingService paymentProcessingService,
            @Value("${payment.processing.batch-size:50}") int batchSize
    ) {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalArgumentException("Payment processing batch size must be between 1 and 500");
        }

        this.paymentRepository = paymentRepository;
        this.paymentProcessingService = paymentProcessingService;
        this.batchSize = batchSize;
    }

    public int processPendingPayments() {
        List<UUID> pendingPaymentIds = paymentRepository.findPendingPaymentIds(
                        PaymentStatus.PENDING,
                        PageRequest.of(0, batchSize));

        int processedPaymentCount = 0;

        for (UUID paymentId : pendingPaymentIds) {
            try {
                // The processing service claims the payment transactionally
                // and calls the provider outside the database transaction
                paymentProcessingService.processPayment(paymentId);

                processedPaymentCount++;

            } catch (PaymentNotPendingException exception) {
                // another worker may have claimed this payment first
                log.debug(
                        "Payment {} is no longer pending",
                        paymentId);

            } catch (Exception exception) {
                // continue with other payments if one attempt fails
                log.warn(
                        "Could not process pending payment {}",
                        paymentId,
                        exception);
            }
        }

        return processedPaymentCount;
    }
}
