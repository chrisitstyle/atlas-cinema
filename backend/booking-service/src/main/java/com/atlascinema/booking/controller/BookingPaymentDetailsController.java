package com.atlascinema.booking.controller;

import com.atlascinema.booking.dto.BookingPaymentDetailsResponse;
import com.atlascinema.booking.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/*
 * Provides booking information to internal payment operations
 * this endpoint is not intended for direct customer access
 */
@RestController
@RequestMapping("/internal/bookings")
@RequiredArgsConstructor
public class BookingPaymentDetailsController {

    private final BookingService bookingService;

    @GetMapping("/{bookingId}/payment-details")
    public BookingPaymentDetailsResponse getBookingPaymentDetails(@PathVariable UUID bookingId) {
        return bookingService.getBookingPaymentDetails(bookingId);
    }
}
