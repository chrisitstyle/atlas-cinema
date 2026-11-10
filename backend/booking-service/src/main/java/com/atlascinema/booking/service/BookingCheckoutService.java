package com.atlascinema.booking.service;

import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.dto.CreateBookingResponse;
import com.atlascinema.booking.security.guest.GuestBookingTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Coordinates booking creation and guest payment token issuance.
 * Keeps guest credentials separate from the booking domain response.
 */
@Service
@RequiredArgsConstructor
public class BookingCheckoutService {

    private final BookingService bookingService;
    private final GuestBookingTokenService guestBookingTokenService;

    public CreateBookingResponse createBooking(
            CreateBookingRequest request,
            UUID authenticatedCustomerId
    ) {
        String guestPaymentToken = null;
        String guestPaymentTokenHash = null;

        if (authenticatedCustomerId == null) {
            guestPaymentToken = guestBookingTokenService.generateToken();

            guestPaymentTokenHash = guestBookingTokenService.hashToken(guestPaymentToken);
        }

        BookingResponse createdBooking = bookingService.createBooking(
                request,
                authenticatedCustomerId,
                guestPaymentTokenHash);

        // Return the original token only after booking persistence succeeds.
        return CreateBookingResponse.from(
                createdBooking,
                guestPaymentToken);
    }
}
