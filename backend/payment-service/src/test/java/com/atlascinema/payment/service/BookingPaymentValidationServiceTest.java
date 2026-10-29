package com.atlascinema.payment.service;

import com.atlascinema.payment.client.BookingClient;
import com.atlascinema.payment.client.dto.BookingPaymentDetailsResponse;
import com.atlascinema.payment.exception.BookingNotPayableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/*
 * Verifies booking eligibility rules before payment initiation
 * Uses a fixed clock to test expiration boundaries
 */
@ExtendWith(MockitoExtension.class)
class BookingPaymentValidationServiceTest {

    private static final UUID BOOKING_ID = UUID.fromString("f5fd3520-c844-4d16-bde3-360e87648dd2");

    private static final Instant CURRENT_TIME = Instant.parse("2026-10-29T10:00:00Z");

    private static final Instant FUTURE_EXPIRATION = CURRENT_TIME.plusSeconds(300);

    private static final BigDecimal TOTAL_PRICE = new BigDecimal("59.80");

    @Mock
    private BookingClient bookingClient;

    private BookingPaymentValidationService bookingPaymentValidationService;

    @BeforeEach
    void setUp() {
        Clock fixedPaymentClock = Clock.fixed(CURRENT_TIME, ZoneOffset.UTC);

        bookingPaymentValidationService = new BookingPaymentValidationService(
                        bookingClient,
                        fixedPaymentClock);
    }

    @Test
    void getPayableBookingDetails_shouldReturnDetails_whenBookingIsHeldAndNotExpired() {
        BookingPaymentDetailsResponse payableBookingDetails = createBookingDetails(
                        "HELD",
                        FUTURE_EXPIRATION,
                        TOTAL_PRICE,
                        "PLN");

        when(bookingClient.getBookingPaymentDetails(BOOKING_ID)).thenReturn(payableBookingDetails);

        BookingPaymentDetailsResponse returnedBookingDetails = bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID);

        assertThat(returnedBookingDetails).isSameAs(payableBookingDetails);

        assertThat(returnedBookingDetails.totalPrice()).isEqualByComparingTo("59.80");

        assertThat(returnedBookingDetails.currencyCode()).isEqualTo("PLN");
    }

    @Test
    void getPayableBookingDetails_shouldRejectBooking_whenStatusIsExpired() {
        BookingPaymentDetailsResponse expiredBookingDetails = createBookingDetails(
                        "EXPIRED",
                        FUTURE_EXPIRATION,
                        TOTAL_PRICE,
                        "PLN");

        when(bookingClient.getBookingPaymentDetails(BOOKING_ID))
                .thenReturn(expiredBookingDetails);

        assertThatThrownBy(() ->
                bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID)
        ).isInstanceOf(BookingNotPayableException.class);
    }

    @Test
    void getPayableBookingDetails_shouldRejectBooking_whenHoldHasExpired() {
        BookingPaymentDetailsResponse expiredBookingDetails = createBookingDetails(
                        "HELD",
                        CURRENT_TIME.minusSeconds(60),
                        TOTAL_PRICE,
                        "PLN");

        when(bookingClient.getBookingPaymentDetails(BOOKING_ID))
                .thenReturn(expiredBookingDetails);

        assertThatThrownBy(() ->
                bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID)
        ).isInstanceOf(BookingNotPayableException.class);
    }

    @Test
    void getPayableBookingDetails_shouldRejectBooking_whenExpirationEqualsCurrentTime() {
        BookingPaymentDetailsResponse expiredBookingDetails = createBookingDetails(
                        "HELD",
                        CURRENT_TIME,
                        TOTAL_PRICE,
                        "PLN");

        when(bookingClient.getBookingPaymentDetails(BOOKING_ID)).thenReturn(expiredBookingDetails);

        assertThatThrownBy(() -> bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID)
        ).isInstanceOf(BookingNotPayableException.class);
    }

    @Test
    void getPayableBookingDetails_shouldRejectBooking_whenExpirationIsMissing() {
        BookingPaymentDetailsResponse invalidBookingDetails = createBookingDetails(
                        "HELD",
                        null,
                        TOTAL_PRICE,
                        "PLN");

        when(bookingClient.getBookingPaymentDetails(BOOKING_ID)).thenReturn(invalidBookingDetails);

        assertThatThrownBy(() -> bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID)
        ).isInstanceOf(BookingNotPayableException.class);
    }

    @Test
    void getPayableBookingDetails_shouldRejectBooking_whenPriceIsZero() {
        BookingPaymentDetailsResponse invalidBookingDetails = createBookingDetails(
                        "HELD",
                        FUTURE_EXPIRATION,
                        BigDecimal.ZERO,
                        "PLN");

        when(bookingClient.getBookingPaymentDetails(BOOKING_ID)).thenReturn(invalidBookingDetails);

        assertThatThrownBy(() -> bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID)
        ).isInstanceOf(BookingNotPayableException.class);
    }

    @Test
    void getPayableBookingDetails_shouldRejectBooking_whenCurrencyIsUnsupported() {
        BookingPaymentDetailsResponse unsupportedCurrencyDetails = createBookingDetails(
                        "HELD",
                        FUTURE_EXPIRATION,
                        TOTAL_PRICE,
                        "EUR");

        when(bookingClient.getBookingPaymentDetails(BOOKING_ID)).thenReturn(unsupportedCurrencyDetails);

        assertThatThrownBy(() -> bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID)
        ).isInstanceOf(BookingNotPayableException.class);
    }

    @Test
    void getPayableBookingDetails_shouldRejectBooking_whenReturnedBookingIdDiffers() {
        BookingPaymentDetailsResponse incorrectBookingDetails = new BookingPaymentDetailsResponse(
                        UUID.randomUUID(),
                        null,
                        "HELD",
                        FUTURE_EXPIRATION,
                        TOTAL_PRICE,
                        "PLN");

        when(bookingClient.getBookingPaymentDetails(BOOKING_ID)).thenReturn(incorrectBookingDetails);

        assertThatThrownBy(() -> bookingPaymentValidationService.getPayableBookingDetails(BOOKING_ID)
        ).isInstanceOf(BookingNotPayableException.class);
    }

    private BookingPaymentDetailsResponse createBookingDetails(
            String bookingStatus,
            Instant bookingExpiresAt,
            BigDecimal totalPrice,
            String currencyCode
    ) {
        return new BookingPaymentDetailsResponse(
                BOOKING_ID,
                null,
                bookingStatus,
                bookingExpiresAt,
                totalPrice,
                currencyCode);
    }
}
