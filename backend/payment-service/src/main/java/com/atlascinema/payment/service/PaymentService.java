
package com.atlascinema.payment.service;

import com.atlascinema.payment.client.dto.BookingPaymentDetailsResponse;
import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.exception.PaymentIdempotencyConflictException;
import com.atlascinema.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/*
 * Coordinates payment initiation using verified booking details
 * Reuses existing payments and handles concurrent idempotency conflicts
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentCreationTransactionService paymentCreationTransactionService;
    private final BookingPaymentValidationService bookingPaymentValidationService;

    public Payment initiatePaymentForBooking(
            UUID bookingId,
            String idempotencyKey
    ) {
        Objects.requireNonNull(bookingId, "Booking id is required");
        Objects.requireNonNull(idempotencyKey, "Idempotency key is required");

        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existingPayment.isPresent()) {
            Payment previouslyInitiatedPayment = existingPayment.get();

            validateMatchingBookingId(
                    previouslyInitiatedPayment,
                    bookingId
            );

            return previouslyInitiatedPayment;
        }

        // price and currency come from booking-service, not the caller
        BookingPaymentDetailsResponse payableBookingDetails =
                bookingPaymentValidationService.getPayableBookingDetails(bookingId);

        return createPaymentOrRecoverExisting(
                bookingId,
                payableBookingDetails.totalPrice(),
                payableBookingDetails.currencyCode(),
                idempotencyKey);
    }

    // recover an existing payment after a concurrent insert conflict
    private Payment createPaymentOrRecoverExisting(
            UUID bookingId,
            BigDecimal totalPrice,
            String currencyCode,
            String idempotencyKey) {
        try {
            return paymentCreationTransactionService.createPendingPayment(
                    bookingId,
                    totalPrice,
                    currencyCode,
                    idempotencyKey);
        } catch (DataIntegrityViolationException databaseConstraintViolation) {
            Optional<Payment> concurrentlyCreatedPayment =
                    paymentRepository.findByIdempotencyKey(idempotencyKey);

            if (concurrentlyCreatedPayment.isEmpty()) {
                throw databaseConstraintViolation;
            }

            Payment existingPayment = concurrentlyCreatedPayment.get();

            validateMatchingPaymentDetails(
                    existingPayment,
                    bookingId,
                    totalPrice,
                    currencyCode);

            return existingPayment;
        }
    }

    // a repeated request must refer to the original booking
    private void validateMatchingBookingId(
            Payment existingPayment,
            UUID requestedBookingId
    ) {
        if (!existingPayment.getBookingId().equals(requestedBookingId)) {
            throw new PaymentIdempotencyConflictException();
        }
    }

    // concurrent requests must agree on the verified payment details
    private void validateMatchingPaymentDetails(
            Payment existingPayment,
            UUID requestedBookingId,
            BigDecimal verifiedAmount,
            String verifiedCurrencyCode
    ) {
        validateMatchingBookingId(existingPayment, requestedBookingId);

        boolean amountMatches = existingPayment.getAmount().compareTo(verifiedAmount) == 0;

        boolean currencyMatches = existingPayment.getCurrencyCode().equals(verifiedCurrencyCode);

        if (!amountMatches || !currencyMatches) {
            throw new PaymentIdempotencyConflictException();
        }
    }
}
