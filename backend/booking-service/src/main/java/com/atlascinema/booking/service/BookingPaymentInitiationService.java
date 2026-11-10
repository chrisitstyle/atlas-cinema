package com.atlascinema.booking.service;

import com.atlascinema.booking.client.PaymentClient;
import com.atlascinema.booking.client.dto.InitiatePaymentRequest;
import com.atlascinema.booking.client.dto.InitiatePaymentResponse;
import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.exception.BookingNotFoundException;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.security.guest.GuestBookingTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Initiates payments after verifying access to the requested booking.
 * Supports authenticated customers and anonymous guests with booking tokens.
 */
@Service
@RequiredArgsConstructor
public class BookingPaymentInitiationService {

    private final BookingRepository bookingRepository;
    private final PaymentClient paymentClient;
    private final GuestBookingTokenService guestBookingTokenService;

    public InitiatePaymentResponse initiateCustomerPayment(
            UUID bookingId,
            UUID authenticatedCustomerId,
            String idempotencyKey
    ) {
        verifyBookingOwnership(bookingId, authenticatedCustomerId);

        return requestPayment(bookingId, idempotencyKey);
    }

    public InitiatePaymentResponse initiateGuestPayment(
            UUID bookingId,
            String providedGuestPaymentToken,
            String idempotencyKey
    ) {
        verifyGuestBookingAccess(bookingId, providedGuestPaymentToken);

        return requestPayment(bookingId, idempotencyKey);
    }

    private InitiatePaymentResponse requestPayment(
            UUID bookingId,
            String idempotencyKey
    ) {
        // feign obtains a technical JWT for booking-service
        return paymentClient.initiatePayment(
                idempotencyKey,
                new InitiatePaymentRequest(bookingId));
    }

    private void verifyBookingOwnership(
            UUID bookingId,
            UUID authenticatedCustomerId
    ) {
        Booking booking = findBooking(bookingId);

        if (!authenticatedCustomerId.equals(booking.getCustomerId())) {
            throw new BookingNotFoundException(bookingId);
        }
    }

    private void verifyGuestBookingAccess(
            UUID bookingId,
            String providedGuestPaymentToken
    ) {
        Booking booking = findBooking(bookingId);

        boolean isGuestBooking = booking.getCustomerId() == null;

        boolean hasValidGuestToken = guestBookingTokenService.matchesToken(
                providedGuestPaymentToken,
                booking.getGuestPaymentTokenHash());

        // do not reveal whether the booking exists or the guest token is invalid
        if (!isGuestBooking || !hasValidGuestToken) {
            throw new BookingNotFoundException(bookingId);
        }
    }

    private Booking findBooking(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }
}
