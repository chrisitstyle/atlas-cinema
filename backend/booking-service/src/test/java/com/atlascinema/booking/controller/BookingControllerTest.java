package com.atlascinema.booking.controller;

import com.atlascinema.booking.client.dto.InitiatePaymentResponse;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.BookingSeatResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.dto.CreateBookingResponse;
import com.atlascinema.booking.service.BookingCheckoutService;
import com.atlascinema.booking.service.BookingPaymentInitiationService;
import com.atlascinema.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests booking controller request handling and service delegation.
 * Covers guest checkout, customer payments and booking cancellation.
 */
@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @Mock
    private BookingCheckoutService bookingCheckoutService;

    @Mock
    private BookingPaymentInitiationService bookingPaymentInitiationService;

    @InjectMocks
    private BookingController bookingController;

    @Test
    void createBooking_shouldReturnGuestToken_whenGuestCreatesBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        String guestPaymentToken = "example-guest-payment-token";

        CreateBookingRequest request = new CreateBookingRequest(
                "guest@example.com",
                screeningId,
                List.of(seatId));

        BookingResponse bookingResponse = createBookingResponse(
                bookingId,
                null,
                screeningId,
                seatId,
                BookingStatus.HELD);

        CreateBookingResponse expectedResponse = CreateBookingResponse.from(
                        bookingResponse,
                        guestPaymentToken);

        when(bookingCheckoutService.createBooking(request, null))
                .thenReturn(expectedResponse);

        ResponseEntity<CreateBookingResponse> result = bookingController.createBooking(request, null);

        assertThat(result.getStatusCode().value()).isEqualTo(201);

        assertThat(result.getHeaders().getLocation())
                .hasToString("/bookings/" + bookingId);

        assertThat(result.getHeaders().getCacheControl()).contains("no-store");

        assertThat(result.getBody()).isEqualTo(expectedResponse);

        assert result.getBody() != null;
        assertThat(result.getBody().guestPaymentToken())
                .isEqualTo(guestPaymentToken);

        verify(bookingCheckoutService).createBooking(request, null);
    }

    @Test
    void createBooking_shouldNotReturnGuestToken_whenCustomerCreatesBooking() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        CreateBookingRequest request = new CreateBookingRequest(
                "customer@example.com",
                screeningId,
                List.of(seatId));

        BookingResponse bookingResponse = createBookingResponse(
                bookingId,
                customerId,
                screeningId,
                seatId,
                BookingStatus.HELD);

        CreateBookingResponse expectedResponse = CreateBookingResponse.from(bookingResponse, null);

        Jwt customerJwt = createCustomerJwt(customerId);

        when(bookingCheckoutService.createBooking(request, customerId))
                .thenReturn(expectedResponse);

        ResponseEntity<CreateBookingResponse> result = bookingController.createBooking(request, customerJwt);

        assertThat(result.getStatusCode().value()).isEqualTo(201);

        assertThat(result.getBody()).isEqualTo(expectedResponse);

        assert result.getBody() != null;
        assertThat(result.getBody().customerId()).isEqualTo(customerId);

        assertThat(result.getBody().guestPaymentToken()).isNull();

        verify(bookingCheckoutService).createBooking(request, customerId);
    }

    @Test
    void initiateGuestPayment_shouldDelegatePaymentWithGuestToken() {
        UUID bookingId = UUID.randomUUID();

        String guestPaymentToken = "example-guest-payment-token";
        String idempotencyKey = UUID.randomUUID().toString();

        InitiatePaymentResponse expectedResponse = createPaymentResponse(bookingId);

        when(bookingPaymentInitiationService.initiateGuestPayment(
                bookingId,
                guestPaymentToken,
                idempotencyKey
        )).thenReturn(expectedResponse);

        InitiatePaymentResponse result = bookingController.initiateGuestPayment(
                        bookingId,
                        idempotencyKey,
                        guestPaymentToken);

        assertThat(result).isEqualTo(expectedResponse);

        verify(bookingPaymentInitiationService).initiateGuestPayment(
                bookingId,
                guestPaymentToken,
                idempotencyKey);
    }

    @Test
    void initiateCustomerPayment_shouldDelegatePaymentWithJwtSubject() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        String idempotencyKey = UUID.randomUUID().toString();

        Jwt customerJwt = createCustomerJwt(customerId);

        InitiatePaymentResponse expectedResponse = createPaymentResponse(bookingId);

        when(bookingPaymentInitiationService.initiateCustomerPayment(
                bookingId,
                customerId,
                idempotencyKey
        )).thenReturn(expectedResponse);

        InitiatePaymentResponse result = bookingController.initiateCustomerPayment(
                        bookingId,
                        idempotencyKey,
                        customerJwt);

        assertThat(result).isEqualTo(expectedResponse);

        verify(bookingPaymentInitiationService).initiateCustomerPayment(
                bookingId,
                customerId,
                idempotencyKey);
    }

    @Test
    void initiateGuestPayment_shouldRejectBlankIdempotencyKey() {
        UUID bookingId = UUID.randomUUID();

        assertThatThrownBy(() ->
                bookingController.initiateGuestPayment(
                        bookingId,
                        " ",
                        "guest-token")
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException responseException = (ResponseStatusException) exception;

                    assertThat(responseException.getStatusCode().value())
                            .isEqualTo(400);
                });

        verify(bookingPaymentInitiationService, never())
                .initiateGuestPayment(
                        bookingId,
                        "guest-token",
                        " ");
    }

    @Test
    void cancelBooking_shouldReturnCancelledBooking_whenBookingCanBeCancelled() {
        UUID bookingId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        BookingResponse expectedResponse = createBookingResponse(
                bookingId,
                null,
                screeningId,
                seatId,
                BookingStatus.CANCELLED);

        when(bookingService.cancelBooking(bookingId))
                .thenReturn(expectedResponse);

        BookingResponse result = bookingController.cancelBooking(bookingId);

        assertThat(result).isEqualTo(expectedResponse);

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);

        verify(bookingService).cancelBooking(bookingId);
    }

    private BookingResponse createBookingResponse(
            UUID bookingId,
            UUID customerId,
            UUID screeningId,
            UUID seatId,
            BookingStatus status
    ) {
        LocalDateTime createdAt =
                LocalDateTime.of(2030, Month.JANUARY, 10, 12, 0);

        LocalDateTime expiresAt = createdAt.plusMinutes(10);

        return new BookingResponse(
                bookingId,
                customerId,
                customerId == null
                        ? "guest@example.com"
                        : "customer@example.com",
                screeningId,
                status,
                createdAt,
                expiresAt,
                new BigDecimal("29.90"),
                List.of(new BookingSeatResponse(
                                UUID.randomUUID(),
                                seatId,
                                new BigDecimal("29.90"))));
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

    private Jwt createCustomerJwt(UUID customerId) {
        return Jwt.withTokenValue("test-customer-token")
                .header("alg", "none")
                .claim("sub", customerId.toString())
                .build();
    }
}
