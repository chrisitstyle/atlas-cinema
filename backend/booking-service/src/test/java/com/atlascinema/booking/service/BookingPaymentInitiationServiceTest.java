
package com.atlascinema.booking.service;

import com.atlascinema.booking.client.PaymentClient;
import com.atlascinema.booking.client.dto.InitiatePaymentRequest;
import com.atlascinema.booking.client.dto.InitiatePaymentResponse;
import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.exception.BookingNotFoundException;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.security.guest.GuestBookingTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingPaymentInitiationServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PaymentClient paymentClient;

    private GuestBookingTokenService guestBookingTokenService;
    private BookingPaymentInitiationService paymentInitiationService;

    private UUID bookingId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        bookingId = UUID.randomUUID();
        idempotencyKey = UUID.randomUUID().toString();

        guestBookingTokenService = new GuestBookingTokenService();

        paymentInitiationService = new BookingPaymentInitiationService(
                bookingRepository,
                paymentClient,
                guestBookingTokenService);
    }

    @Test
    void initiateGuestPayment_shouldCallPaymentClientForValidToken() {
        String guestToken = guestBookingTokenService.generateToken();

        Booking booking = createGuestBooking(bookingId, guestToken);

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        InitiatePaymentResponse expectedResponse = createPaymentResponse(bookingId);

        when(paymentClient.initiatePayment(
                eq(idempotencyKey),
                any(InitiatePaymentRequest.class)
        )).thenReturn(expectedResponse);

        InitiatePaymentResponse response = paymentInitiationService.initiateGuestPayment(
                        bookingId,
                        guestToken,
                        idempotencyKey);

        assertThat(response).isEqualTo(expectedResponse);

        ArgumentCaptor<InitiatePaymentRequest> requestCaptor = ArgumentCaptor.forClass(InitiatePaymentRequest.class);

        verify(paymentClient).initiatePayment(
                eq(idempotencyKey),
                requestCaptor.capture());

        assertThat(requestCaptor.getValue().bookingId()).isEqualTo(bookingId);
    }

    @Test
    void initiateGuestPayment_shouldRejectInvalidToken() {
        String validToken = guestBookingTokenService.generateToken();
        String invalidToken = guestBookingTokenService.generateToken();

        Booking booking = createGuestBooking(bookingId, validToken);

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() ->
                paymentInitiationService.initiateGuestPayment(
                        bookingId,
                        invalidToken,
                        idempotencyKey)
        ).isInstanceOf(BookingNotFoundException.class);

        verifyNoInteractions(paymentClient);
    }

    @Test
    void initiateGuestPayment_shouldRejectMissingToken() {
        String validToken = guestBookingTokenService.generateToken();

        Booking booking = createGuestBooking(bookingId, validToken);

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() ->
                paymentInitiationService.initiateGuestPayment(
                        bookingId,
                        null,
                        idempotencyKey)
        ).isInstanceOf(BookingNotFoundException.class);

        verifyNoInteractions(paymentClient);
    }

    @Test
    void initiateGuestPayment_shouldRejectTokenFromAnotherBooking() {
        String firstToken = guestBookingTokenService.generateToken();
        String secondToken = guestBookingTokenService.generateToken();

        Booking booking = createGuestBooking(bookingId, firstToken);

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() ->
                paymentInitiationService.initiateGuestPayment(
                        bookingId,
                        secondToken,
                        idempotencyKey)
        ).isInstanceOf(BookingNotFoundException.class);

        verifyNoInteractions(paymentClient);
    }

    @Test
    void initiateGuestPayment_shouldRejectCustomerBooking() {
        Booking customerBooking = Booking.create(
                UUID.randomUUID(),
                "customer@example.com",
                UUID.randomUUID(),
                LocalDateTime.of(2030, Month.JANUARY, 10, 12, 0));

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(customerBooking));

        assertThatThrownBy(() ->
                paymentInitiationService.initiateGuestPayment(
                        bookingId,
                        guestBookingTokenService.generateToken(),
                        idempotencyKey)
        ).isInstanceOf(BookingNotFoundException.class);

        verifyNoInteractions(paymentClient);
    }

    @Test
    void initiateGuestPayment_shouldRejectBookingWithoutStoredToken() {
        Booking booking = Booking.create(
                null,
                "guest@example.com",
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 12, 0));

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() ->
                paymentInitiationService.initiateGuestPayment(
                        bookingId,
                        guestBookingTokenService.generateToken(),
                        idempotencyKey)
        ).isInstanceOf(BookingNotFoundException.class);

        verifyNoInteractions(paymentClient);
    }

    @Test
    void initiateGuestPayment_shouldRejectNonexistentBooking() {
        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentInitiationService.initiateGuestPayment(
                        bookingId,
                        guestBookingTokenService.generateToken(),
                        idempotencyKey)
        ).isInstanceOf(BookingNotFoundException.class);

        verifyNoInteractions(paymentClient);
    }

    @Test
    void initiateCustomerPayment_shouldCallPaymentClientForOwner() {
        UUID customerId = UUID.randomUUID();

        Booking booking = Booking.create(
                customerId,
                "customer@example.com",
                UUID.randomUUID(),
                LocalDateTime.of(2030, Month.JANUARY, 10, 12, 0));

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        InitiatePaymentResponse expectedResponse = createPaymentResponse(bookingId);

        when(paymentClient.initiatePayment(
                eq(idempotencyKey),
                any(InitiatePaymentRequest.class)
        )).thenReturn(expectedResponse);

        InitiatePaymentResponse response = paymentInitiationService.initiateCustomerPayment(
                        bookingId,
                        customerId,
                        idempotencyKey);

        assertThat(response).isEqualTo(expectedResponse);

        verify(paymentClient).initiatePayment(
                eq(idempotencyKey),
                eq(new InitiatePaymentRequest(bookingId)));
    }

    @Test
    void initiateCustomerPayment_shouldRejectDifferentCustomer() {
        UUID bookingOwnerId = UUID.randomUUID();
        UUID authenticatedCustomerId = UUID.randomUUID();

        Booking booking = Booking.create(
                bookingOwnerId,
                "customer@example.com",
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 12, 0));

        when(bookingRepository.findById(bookingId))
                .thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentInitiationService.initiateCustomerPayment(
                        bookingId,
                        authenticatedCustomerId,
                        idempotencyKey)
        ).isInstanceOf(BookingNotFoundException.class);

        verifyNoInteractions(paymentClient);
    }

    private Booking createGuestBooking(
            UUID requestedBookingId,
            String guestToken
    ) {
        Booking booking = Booking.create(
                null,
                "guest@example.com",
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 12, 0));

        booking.assignGuestPaymentTokenHash(guestBookingTokenService.hashToken(guestToken));

        return booking;
    }

    private InitiatePaymentResponse createPaymentResponse(
            UUID requestedBookingId
    ) {
        return new InitiatePaymentResponse(
                UUID.randomUUID(),
                requestedBookingId,
                new BigDecimal("29.90"),
                "PLN",
                "PENDING",
                Instant.parse("2030-01-10T12:00:00Z"));
    }
}
