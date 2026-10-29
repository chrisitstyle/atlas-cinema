package com.atlascinema.payment.client;

import com.atlascinema.payment.client.dto.BookingPaymentDetailsResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/*
 * Retrieves booking information required for payment initiation
 * Resolves booking-service through Eureka and Spring Cloud LoadBalancer
 */
@FeignClient(name = "booking-service")
public interface BookingClient {

    @GetMapping("/internal/bookings/{bookingId}/payment-details")
    BookingPaymentDetailsResponse getBookingPaymentDetails(@PathVariable("bookingId") UUID bookingId);
}
