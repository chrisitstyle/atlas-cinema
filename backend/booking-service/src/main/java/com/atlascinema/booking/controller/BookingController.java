
package com.atlascinema.booking.controller;

import com.atlascinema.booking.client.dto.InitiatePaymentResponse;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.dto.CreateBookingResponse;
import com.atlascinema.booking.service.BookingCheckoutService;
import com.atlascinema.booking.service.BookingPaymentInitiationService;
import com.atlascinema.booking.service.BookingService;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Exposes booking creation, customer history and payment initiation.
 * Supports JWT ownership checks and token-based guest payment access.
 */
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingCheckoutService bookingCheckoutService;
    private final BookingPaymentInitiationService bookingPaymentInitiationService;

    @PostMapping
    public ResponseEntity<CreateBookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal Jwt authenticatedUserJwt
    ) {
        UUID authenticatedCustomerId = authenticatedUserJwt == null
                ? null
                : UUID.fromString(authenticatedUserJwt.getSubject());

        CreateBookingResponse createdBooking = bookingCheckoutService.createBooking(
                request,
                authenticatedCustomerId);

        // the creation response may contain a guest credential and must not be cached
        return ResponseEntity
                .created(URI.create("/bookings/" + createdBooking.id()))
                .cacheControl(CacheControl.noStore())
                .body(createdBooking);
    }

    @GetMapping("/me")
    public List<BookingResponse> getMyBookings(
            @AuthenticationPrincipal Jwt authenticatedUserJwt
    ) {
        UUID authenticatedCustomerId = UUID.fromString(authenticatedUserJwt.getSubject());

        return bookingService.getCustomerBookings(authenticatedCustomerId);
    }

    @PostMapping("/{bookingId}/payments")
    public InitiatePaymentResponse initiateCustomerPayment(
            @PathVariable UUID bookingId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt authenticatedCustomerJwt
    ) {
        validateIdempotencyKey(idempotencyKey);

        UUID authenticatedCustomerId = UUID.fromString(authenticatedCustomerJwt.getSubject());

        return initiatePayment(() -> bookingPaymentInitiationService.initiateCustomerPayment(
                bookingId,
                authenticatedCustomerId,
                idempotencyKey));
    }

    @PostMapping("/{bookingId}/guest-payments")
    public InitiatePaymentResponse initiateGuestPayment(
            @PathVariable UUID bookingId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(
                    name = "X-Guest-Booking-Token",
                    required = false)
            String guestPaymentToken
    ) {
        validateIdempotencyKey(idempotencyKey);

        return initiatePayment(() -> bookingPaymentInitiationService.initiateGuestPayment(
                bookingId,
                guestPaymentToken,
                idempotencyKey));
    }

    @PatchMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(
            @PathVariable UUID bookingId
    ) {
        return bookingService.cancelBooking(bookingId);
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain between 1 and 128 characters");
        }
    }

    private InitiatePaymentResponse initiatePayment(
            Supplier<InitiatePaymentResponse> paymentInitiation
    ) {
        try {
            return paymentInitiation.get();

        } catch (FeignException.Conflict exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment initiation was rejected",
                    exception);

        } catch (FeignException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment service request failed",
                    exception);
        }
    }
}
