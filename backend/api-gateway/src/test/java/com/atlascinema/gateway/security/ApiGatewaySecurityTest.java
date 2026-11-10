package com.atlascinema.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verifies API Gateway authorization using reactive HTTP requests.
 * Covers guest access, customer permissions and staff-only operations.
 */
@WebFluxTest
@Import({
        SecurityConfig.class,
        ApiGatewaySecurityTest.JwtDecoderTestConfiguration.class,
        ApiGatewaySecurityTest.TestEndpoints.class
})
class ApiGatewaySecurityTest {

    private static final String CUSTOMER_TOKEN = "customer-token";
    private static final String MANAGER_TOKEN = "manager-token";

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void createBooking_shouldAllowRequestWithoutJwt() {
        webTestClient.post()
                .uri("/bookings")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void guestPayment_shouldAllowRequestWithoutJwt() {
        UUID bookingId = UUID.randomUUID();

        webTestClient.post()
                .uri("/bookings/{bookingId}/guest-payments", bookingId)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Guest-Booking-Token", "example-guest-token")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void guestPayment_shouldRejectInvalidBearerToken() {
        UUID bookingId = UUID.randomUUID();

        webTestClient.post()
                .uri("/bookings/{bookingId}/guest-payments", bookingId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-jwt")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("X-Guest-Booking-Token", "example-guest-token")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void customerPayment_shouldRejectRequestWithoutJwt() {
        UUID bookingId = UUID.randomUUID();

        webTestClient.post()
                .uri("/bookings/{bookingId}/payments", bookingId)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void customerPayment_shouldRejectManagerWithoutCustomerRole() {
        UUID bookingId = UUID.randomUUID();

        webTestClient.post()
                .uri("/bookings/{bookingId}/payments", bookingId)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + MANAGER_TOKEN)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void customerPayment_shouldAllowAuthenticatedCustomer() {
        UUID bookingId = UUID.randomUUID();

        webTestClient.post()
                .uri("/bookings/{bookingId}/payments", bookingId)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + CUSTOMER_TOKEN)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void customerBookingHistory_shouldRejectRequestWithoutJwt() {
        webTestClient.get()
                .uri("/bookings/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void customerBookingHistory_shouldAllowCustomer() {
        webTestClient.get()
                .uri("/bookings/me")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + CUSTOMER_TOKEN)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void cancelBooking_shouldRejectCustomerWithoutStaffRole() {
        UUID bookingId = UUID.randomUUID();

        webTestClient.patch()
                .uri("/bookings/{bookingId}/cancel", bookingId)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + CUSTOMER_TOKEN)
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void cancelBooking_shouldAllowManager() {
        UUID bookingId = UUID.randomUUID();

        webTestClient.patch()
                .uri("/bookings/{bookingId}/cancel", bookingId)
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + MANAGER_TOKEN)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void internalPaymentEndpoint_shouldRejectCustomer() {
        webTestClient.post()
                .uri("/internal/payments")
                .header(
                        HttpHeaders.AUTHORIZATION,
                        "Bearer " + CUSTOMER_TOKEN)
                .exchange()
                .expectStatus().isForbidden();
    }

    /**
     * Provides deterministic JWT decoding without connecting to Keycloak
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class JwtDecoderTestConfiguration {

        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return tokenValue -> {
                if (CUSTOMER_TOKEN.equals(tokenValue)) {
                    return Mono.just(createJwt(
                            tokenValue,
                            "CUSTOMER"));
                }

                if (MANAGER_TOKEN.equals(tokenValue)) {
                    return Mono.just(createJwt(
                            tokenValue,
                            "MANAGER"));
                }

                return Mono.error(new BadJwtException("Invalid test JWT"));
            };
        }

        private Jwt createJwt(
                String tokenValue,
                String realmRole
        ) {
            return Jwt.withTokenValue(tokenValue)
                    .header("alg", "RS256")
                    .subject(UUID.randomUUID().toString())
                    .issuedAt(Instant.parse("2030-01-10T11:00:00Z"))
                    .expiresAt(Instant.parse("2030-01-10T13:00:00Z"))
                    .claim("realm_access",
                            Map.of("roles", List.of(realmRole)))
                    .build();
        }
    }

    /**
     * Provides test-only HTTP handlers without routing to microservices.
     */
    @RestController
    static class TestEndpoints {

        @PostMapping("/bookings")
        public Mono<String> createBooking() {
            return Mono.just("OK");
        }

        @PostMapping("/bookings/{bookingId}/guest-payments")
        public Mono<String> initiateGuestPayment(
                @PathVariable UUID bookingId
        ) {
            return Mono.just(bookingId.toString());
        }

        @PostMapping("/bookings/{bookingId}/payments")
        public Mono<String> initiateCustomerPayment(
                @PathVariable UUID bookingId
        ) {
            return Mono.just(bookingId.toString());
        }

        @GetMapping("/bookings/me")
        public Mono<String> getMyBookings() {
            return Mono.just("OK");
        }

        @PatchMapping("/bookings/{bookingId}/cancel")
        public Mono<String> cancelBooking(
                @PathVariable UUID bookingId
        ) {
            return Mono.just(bookingId.toString());
        }

        @PostMapping("/internal/payments")
        public Mono<String> initiateInternalPayment() {
            return Mono.just("OK");
        }
    }
}
