
package com.atlascinema.booking.security;

import com.atlascinema.booking.client.dto.InitiatePaymentResponse;
import com.atlascinema.booking.controller.BookingController;
import com.atlascinema.booking.controller.BookingPaymentDetailsController;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.dto.BookingPaymentDetailsResponse;
import com.atlascinema.booking.exception.BookingNotFoundException;
import com.atlascinema.booking.exception.GlobalExceptionHandler;
import com.atlascinema.booking.service.BookingCheckoutService;
import com.atlascinema.booking.service.BookingPaymentInitiationService;
import com.atlascinema.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies booking endpoint authorization through Spring Security filters.
 * Tests guest access, customer ownership and technical service permissions.
 */
@WebMvcTest(controllers = {
        BookingController.class,
        BookingPaymentDetailsController.class
})
@Import({
        SecurityConfig.class,
        GlobalExceptionHandler.class
})
class BookingHttpSecurityTest {

    private static final String IDEMPOTENCY_KEY = "test-payment-idempotency-key";

    private static final String GUEST_TOKEN = "example-guest-booking-token";

    private static final String PAYMENT_SERVICE_CLIENT_ID = "atlas-cinema-payment-service";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private BookingCheckoutService bookingCheckoutService;

    @MockitoBean
    private BookingPaymentInitiationService bookingPaymentInitiationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void guestPayment_shouldAllowRequestWithoutJwt_whenGuestTokenIsValid()
            throws Exception {

        UUID bookingId = UUID.randomUUID();

        InitiatePaymentResponse expectedResponse = createPaymentResponse(bookingId);

        when(bookingPaymentInitiationService.initiateGuestPayment(
                bookingId,
                GUEST_TOKEN,
                IDEMPOTENCY_KEY
        )).thenReturn(expectedResponse);

        mockMvc.perform(post("/bookings/{bookingId}/guest-payments", bookingId)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .header("X-Guest-Booking-Token", GUEST_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId")
                        .value(expectedResponse.paymentId().toString()))
                .andExpect(jsonPath("$.bookingId")
                        .value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(bookingPaymentInitiationService).initiateGuestPayment(
                bookingId,
                GUEST_TOKEN,
                IDEMPOTENCY_KEY);

        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void guestPayment_shouldReturnNotFound_whenGuestTokenIsInvalid() throws Exception {

        UUID bookingId = UUID.randomUUID();

        String invalidGuestToken = "invalid-guest-token";

        when(bookingPaymentInitiationService.initiateGuestPayment(
                bookingId,
                invalidGuestToken,
                IDEMPOTENCY_KEY
        )).thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(post("/bookings/{bookingId}/guest-payments", bookingId)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .header(
                                        "X-Guest-Booking-Token",
                                        invalidGuestToken))
                .andExpect(status().isNotFound());

        verify(bookingPaymentInitiationService).initiateGuestPayment(
                bookingId,
                invalidGuestToken,
                IDEMPOTENCY_KEY);
    }

    @Test
    void guestPayment_shouldRejectInvalidBearerToken() throws Exception {

        UUID bookingId = UUID.randomUUID();

        when(jwtDecoder.decode("invalid-jwt"))
                .thenThrow(new BadJwtException("Invalid JWT"));

        mockMvc.perform(post("/bookings/{bookingId}/guest-payments", bookingId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer invalid-jwt")
                                .header("Idempotency-Key", IDEMPOTENCY_KEY)
                                .header("X-Guest-Booking-Token", GUEST_TOKEN)
                ).andExpect(status().isUnauthorized());

        verifyNoInteractions(bookingPaymentInitiationService);
    }

    @Test
    void customerPayment_shouldRejectRequestWithoutJwt() throws Exception {

        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(post("/bookings/{bookingId}/payments", bookingId)
                                .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookingPaymentInitiationService);
    }

    @Test
    void customerPayment_shouldRejectManagerWithoutCustomerRole() throws Exception {

        UUID bookingId = UUID.randomUUID();

        registerJwt(
                "manager-token",
                UUID.randomUUID().toString(),
                null,
                "MANAGER");

        mockMvc.perform(post("/bookings/{bookingId}/payments", bookingId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer manager-token")
                                .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookingPaymentInitiationService);
    }

    @Test
    void customerPayment_shouldAllowAuthenticatedCustomer()
            throws Exception {

        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        registerJwt(
                "customer-token",
                customerId.toString(),
                null,
                "CUSTOMER");

        InitiatePaymentResponse expectedResponse = createPaymentResponse(bookingId);

        when(bookingPaymentInitiationService.initiateCustomerPayment(
                bookingId,
                customerId,
                IDEMPOTENCY_KEY
        )).thenReturn(expectedResponse);

        mockMvc.perform(post("/bookings/{bookingId}/payments", bookingId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer customer-token")
                                .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId")
                        .value(expectedResponse.paymentId().toString()))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(bookingPaymentInitiationService).initiateCustomerPayment(
                bookingId,
                customerId,
                IDEMPOTENCY_KEY);
    }

    @Test
    void customerPayment_shouldReturnNotFoundForAnotherCustomersBooking() throws Exception {

        UUID bookingId = UUID.randomUUID();
        UUID authenticatedCustomerId = UUID.randomUUID();

        registerJwt(
                "other-customer-token",
                authenticatedCustomerId.toString(),
                null,
                "CUSTOMER");

        when(bookingPaymentInitiationService.initiateCustomerPayment(
                bookingId,
                authenticatedCustomerId,
                IDEMPOTENCY_KEY
        )).thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(post("/bookings/{bookingId}/payments", bookingId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer other-customer-token")
                                .header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void paymentDetails_shouldRejectRequestWithoutJwt() throws Exception {

        UUID bookingId = UUID.randomUUID();

        mockMvc.perform(get("/internal/bookings/{bookingId}/payment-details",
                                bookingId))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(bookingService);
    }

    @Test
    void paymentDetails_shouldRejectCustomerToken() throws Exception {

        UUID bookingId = UUID.randomUUID();

        registerJwt(
                "customer-details-token",
                UUID.randomUUID().toString(),
                null,
                "CUSTOMER");

        mockMvc.perform(get("/internal/bookings/{bookingId}/payment-details",
                        bookingId
                        ).header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer customer-details-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookingService);
    }

    @Test
    void paymentDetails_shouldAllowAuthorizedPaymentService()
            throws Exception {

        UUID bookingId = UUID.randomUUID();

        registerJwt(
                "payment-service-token",
                "payment-service-account",
                PAYMENT_SERVICE_CLIENT_ID,
                "SERVICE_PAYMENT_DETAILS_READ");

        BookingPaymentDetailsResponse expectedResponse = new BookingPaymentDetailsResponse(
                        bookingId,
                        null,
                        BookingStatus.HELD,
                        Instant.parse("2030-01-10T12:10:00Z"),
                        new BigDecimal("29.90"),
                        "PLN");

        when(bookingService.getBookingPaymentDetails(bookingId))
                .thenReturn(expectedResponse);

        mockMvc.perform(get("/internal/bookings/{bookingId}/payment-details",
                                bookingId
                        ).header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer payment-service-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId")
                        .value(bookingId.toString()))
                .andExpect(jsonPath("$.status").value("HELD"))
                .andExpect(jsonPath("$.currencyCode").value("PLN"));

        verify(bookingService).getBookingPaymentDetails(bookingId);
    }

    @Test
    void paymentDetails_shouldRejectUnexpectedOAuth2Client() throws Exception {

        UUID bookingId = UUID.randomUUID();

        registerJwt(
                "wrong-client-token",
                "unexpected-service-account",
                "atlas-cinema-unexpected-service",
                "SERVICE_PAYMENT_DETAILS_READ");

        mockMvc.perform(get("/internal/bookings/{bookingId}/payment-details",
                                bookingId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer wrong-client-token"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookingService);
    }

    @Test
    void paymentDetails_shouldRejectPaymentServiceWithoutRequiredRole() throws Exception {

        UUID bookingId = UUID.randomUUID();

        registerJwt(
                "payment-service-without-role",
                "payment-service-account",
                PAYMENT_SERVICE_CLIENT_ID);

        mockMvc.perform(get("/internal/bookings/{bookingId}/payment-details",
                                bookingId)
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer payment-service-without-role"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(bookingService);
    }

    private void registerJwt(
            String tokenValue,
            String subject,
            String authorizedClientId,
            String... realmRoles
    ) {
        Jwt.Builder jwtBuilder = Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .subject(subject)
                .issuedAt(Instant.parse("2030-01-10T11:00:00Z"))
                .expiresAt(Instant.parse("2030-01-10T13:00:00Z"))
                .claim(
                        "realm_access",
                        Map.of("roles", List.of(realmRoles)));

        if (authorizedClientId != null) {
            jwtBuilder.claim("azp", authorizedClientId);
        }

        when(jwtDecoder.decode(tokenValue)).thenReturn(jwtBuilder.build());
    }

    private InitiatePaymentResponse createPaymentResponse(
            UUID bookingId
    ) {
        return new InitiatePaymentResponse(
                UUID.randomUUID(),
                bookingId,
                new BigDecimal("29.90"),
                "PLN",
                "PENDING",
                Instant.parse("2030-01-10T12:00:00Z"));
    }
}
