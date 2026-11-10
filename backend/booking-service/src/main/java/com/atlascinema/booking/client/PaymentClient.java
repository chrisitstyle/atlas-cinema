package com.atlascinema.booking.client;

import com.atlascinema.booking.client.dto.InitiatePaymentRequest;
import com.atlascinema.booking.client.dto.InitiatePaymentResponse;
import com.atlascinema.booking.security.oauth2.PaymentServiceFeignConfiguration;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Provides authenticated communication with payment-service.
 * Uses Eureka for service discovery and OAuth2 for service authentication.
 */
@FeignClient(
        name = "payment-service",
        configuration = PaymentServiceFeignConfiguration.class
)
public interface PaymentClient {

    @PostMapping("/internal/payments")
    InitiatePaymentResponse initiatePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody InitiatePaymentRequest request);
}
