package com.atlascinema.booking.controller;

import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

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

    @PatchMapping("/{bookingId}/cancel")
    public BookingResponse cancelBooking(@PathVariable UUID bookingId) {
        return bookingService.cancelBooking(bookingId);
    }
}
