
package com.atlascinema.booking.service;

import com.atlascinema.booking.client.PaymentClient;
import com.atlascinema.booking.client.dto.InitiatePaymentRequest;
import com.atlascinema.booking.client.dto.InitiatePaymentResponse;
import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.exception.BookingNotFoundException;
import com.atlascinema.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Initiates customer payments after verifying booking ownership.
 * Delegates payment eligibility and processing to payment-service.
 */
@Service
@RequiredArgsConstructor
public class BookingPaymentInitiationService {

    private final BookingRepository bookingRepository;
    private final PaymentClient paymentClient;

    public InitiatePaymentResponse initiateCustomerPayment(
            UUID bookingId,
            UUID authenticatedCustomerId,
            String idempotencyKey
    ) {
        verifyBookingOwnership(bookingId, authenticatedCustomerId);

        // payment-service independently validates the booking status and expiration
        return paymentClient.initiatePayment(
                idempotencyKey,
                new InitiatePaymentRequest(bookingId));
    }

    private void verifyBookingOwnership(
            UUID bookingId,
            UUID authenticatedCustomerId
    ) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        if (!authenticatedCustomerId.equals(booking.getCustomerId())) {
            // avoid revealing whether another customer's booking exists
            throw new BookingNotFoundException(bookingId);
        }
    }
}
