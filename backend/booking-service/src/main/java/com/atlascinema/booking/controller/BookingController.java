package com.atlascinema.booking.controller;

import com.atlascinema.booking.client.dto.InitiatePaymentResponse;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.service.BookingPaymentInitiationService;
import com.atlascinema.booking.service.BookingService;
import feign.FeignException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Exposes guest checkout, authenticated customer history and staff cancellation.
 * Never accepts the booking owner identifier from the HTTP request body.
 */
@RestController
@RequestMapping("/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;
    private final BookingPaymentInitiationService bookingPaymentInitiationService;

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            @AuthenticationPrincipal Jwt authenticatedUserJwt
    ) {
        UUID authenticatedCustomerId = authenticatedUserJwt == null
                ? null
                : UUID.fromString(authenticatedUserJwt.getSubject());

        BookingResponse createdBooking = bookingService.createBooking(
                request,
                authenticatedCustomerId);

        return ResponseEntity.created(URI.create("/bookings/" + createdBooking.id())
        ).body(createdBooking);
    }

    @GetMapping("/me")
    public List<BookingResponse> getMyBookings(@AuthenticationPrincipal Jwt authenticatedUserJwt) {
        UUID authenticatedCustomerId = UUID.fromString(authenticatedUserJwt.getSubject());
        return bookingService.getCustomerBookings(authenticatedCustomerId);
    }

    @PostMapping("/{bookingId}/payments")
    public InitiatePaymentResponse initiateCustomerPayment(
            @PathVariable UUID bookingId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt authenticatedCustomerJwt
    ) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain between 1 and 128 characters");
        }

        UUID authenticatedCustomerId = UUID.fromString(authenticatedCustomerJwt.getSubject());

        try {
            return bookingPaymentInitiationService.initiateCustomerPayment(
                    bookingId,
                    authenticatedCustomerId,
                    idempotencyKey);
        } catch (FeignException.Conflict exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment initiation was rejected",
                    exception
            );
        } catch (FeignException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Payment service request failed",
                    exception);
        }
    }



    @PatchMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(@PathVariable UUID bookingId) {
        return bookingService.cancelBooking(bookingId);
    }
}
