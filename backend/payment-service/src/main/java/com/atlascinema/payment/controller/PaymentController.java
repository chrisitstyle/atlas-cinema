package com.atlascinema.payment.controller;

import com.atlascinema.payment.client.dto.InitiatePaymentRequest;
import com.atlascinema.payment.client.dto.PaymentResponse;
import com.atlascinema.payment.domain.Payment;
import com.atlascinema.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/*
 * Provides an internal HTTP entry point for payment initiation
 * Public access requires authentication and booking authorization first
 */
@RestController
@RequestMapping("/internal/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public PaymentResponse initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest initiatePaymentRequest,
            @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        // reject invalid keys before calling application services
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain between 1 and 128 characters");
        }

        Payment initiatedPayment = paymentService.initiatePaymentForBooking(
                initiatePaymentRequest.bookingId(),
                idempotencyKey);

        return PaymentResponse.from(initiatedPayment);
    }
}
