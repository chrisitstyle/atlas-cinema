package com.atlascinema.payment.service;

import com.atlascinema.payment.client.BookingClient;
import com.atlascinema.payment.client.dto.BookingPaymentDetailsResponse;
import com.atlascinema.payment.exception.BookingNotPayableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/*
 * Verifies whether a booking is eligible for payment initiation
 * Uses booking-service as the source of booking status and price
 */
@Service
@RequiredArgsConstructor
public class BookingPaymentValidationService {

    private static final String PAYABLE_BOOKING_STATUS = "HELD";
    private static final String SUPPORTED_CURRENCY_CODE = "PLN";

    private final BookingClient bookingClient;
    private final Clock paymentClock;

    public BookingPaymentDetailsResponse getPayableBookingDetails(UUID bookingId) {
        Objects.requireNonNull(bookingId, "Booking id is required");

        BookingPaymentDetailsResponse bookingDetails = bookingClient.getBookingPaymentDetails(bookingId);

        validateBookingDetails(bookingId, bookingDetails);

        return bookingDetails;
    }

    private void validateBookingDetails(
            UUID requestedBookingId,
            BookingPaymentDetailsResponse bookingDetails
    ) {
        if (bookingDetails == null) {
            throw new BookingNotPayableException(
                    requestedBookingId,
                    "booking details are missing");
        }

        // ensure the remote response belongs to the requested booking
        if (!requestedBookingId.equals(bookingDetails.bookingId())) {
            throw new BookingNotPayableException(
                    requestedBookingId,
                    "booking identifier does not match");
        }

        if (!PAYABLE_BOOKING_STATUS.equals(bookingDetails.status())) {
            throw new BookingNotPayableException(
                    requestedBookingId,
                    "booking status must be HELD");
        }

        Instant bookingExpiresAt = bookingDetails.expiresAt();

        if (bookingExpiresAt == null || !bookingExpiresAt.isAfter(paymentClock.instant())) {
            throw new BookingNotPayableException(
                    requestedBookingId,
                    "booking hold has expired");
        }

        BigDecimal bookingTotalPrice = bookingDetails.totalPrice();

        if (bookingTotalPrice == null
                || bookingTotalPrice.signum() <= 0
                || bookingTotalPrice.scale() > 2) {
            throw new BookingNotPayableException(
                    requestedBookingId,
                    "booking total price is invalid");
        }

        if (!SUPPORTED_CURRENCY_CODE.equals(bookingDetails.currencyCode())) {
            throw new BookingNotPayableException(
                    requestedBookingId,
                    "booking currency is not supported");
        }
    }
}
