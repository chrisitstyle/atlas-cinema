package com.atlascinema.payment.service;

import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.UUID;

/*
 * Creates a pending payment in an independent database transaction
 * A database constraint violation rolls back before the caller handles it
 */
@Service
@RequiredArgsConstructor
public class PaymentCreationTransactionService {

    private final PaymentRepository paymentRepository;
    private final Clock paymentClock;

    @Transactional
    public Payment createPendingPayment(
            UUID bookingId,
            BigDecimal totalPrice,
            String currencyCode,
            String idempotencyKey) {
        Payment pendingPayment = Payment.initiateForBooking(
                bookingId,
                totalPrice,
                currencyCode,
                idempotencyKey,
                paymentClock.instant());

        return paymentRepository.saveAndFlush(pendingPayment);
    }
}
