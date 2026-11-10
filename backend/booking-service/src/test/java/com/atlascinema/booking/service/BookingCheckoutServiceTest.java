
package com.atlascinema.booking.service;

import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.BookingSeatResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.dto.CreateBookingResponse;
import com.atlascinema.booking.security.guest.GuestBookingTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingCheckoutServiceTest {

    private static final String GUEST_TOKEN = "example-guest-token";
    private static final String GUEST_TOKEN_HASH = "a".repeat(64);

    @Mock
    private BookingService bookingService;

    @Mock
    private GuestBookingTokenService guestBookingTokenService;

    @InjectMocks
    private BookingCheckoutService bookingCheckoutService;

    private UUID screeningId;
    private UUID seatId;
    private CreateBookingRequest request;

    @BeforeEach
    void setUp() {
        screeningId = UUID.randomUUID();
        seatId = UUID.randomUUID();

        request = new CreateBookingRequest(
                "guest@example.com",
                screeningId,
                List.of(seatId));
    }

    @Test
    void createBooking_shouldGenerateAndReturnToken_whenGuestCreatesBooking() {
        BookingResponse bookingResponse = createBookingResponse(null);

        when(guestBookingTokenService.generateToken())
                .thenReturn(GUEST_TOKEN);

        when(guestBookingTokenService.hashToken(GUEST_TOKEN))
                .thenReturn(GUEST_TOKEN_HASH);

        when(bookingService.createBooking(
                request,
                null,
                GUEST_TOKEN_HASH
        )).thenReturn(bookingResponse);

        CreateBookingResponse result = bookingCheckoutService.createBooking(
                request,
                null);

        assertThat(result.id()).isEqualTo(bookingResponse.id());
        assertThat(result.customerId()).isNull();
        assertThat(result.guestPaymentToken()).isEqualTo(GUEST_TOKEN);

        verify(guestBookingTokenService).generateToken();
        verify(guestBookingTokenService).hashToken(GUEST_TOKEN);

        // only the token hash is passed to the persistence layer
        verify(bookingService).createBooking(
                request,
                null,
                GUEST_TOKEN_HASH);
    }

    @Test
    void createBooking_shouldNotGenerateToken_whenCustomerCreatesBooking() {
        UUID customerId = UUID.randomUUID();

        BookingResponse bookingResponse = createBookingResponse(customerId);

        when(bookingService.createBooking(
                request,
                customerId,
                null
        )).thenReturn(bookingResponse);

        CreateBookingResponse result = bookingCheckoutService.createBooking(
                request,
                customerId);

        assertThat(result.customerId()).isEqualTo(customerId);
        assertThat(result.guestPaymentToken()).isNull();

        verifyNoInteractions(guestBookingTokenService);

        verify(bookingService).createBooking(
                request,
                customerId,
                null);
    }

    @Test
    void createBooking_shouldPropagateFailure_whenBookingCreationFails() {
        when(guestBookingTokenService.generateToken())
                .thenReturn(GUEST_TOKEN);

        when(guestBookingTokenService.hashToken(GUEST_TOKEN))
                .thenReturn(GUEST_TOKEN_HASH);

        when(bookingService.createBooking(
                request,
                null,
                GUEST_TOKEN_HASH
        )).thenThrow(new IllegalStateException("Booking creation failed"));

        assertThatThrownBy(() -> bookingCheckoutService.createBooking(request, null)
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Booking creation failed");

        verify(bookingService).createBooking(
                request,
                null,
                GUEST_TOKEN_HASH);
    }

    private BookingResponse createBookingResponse(UUID customerId) {
        LocalDateTime createdAt = LocalDateTime.of(2030, Month.JANUARY, 10, 12, 0);

        return new BookingResponse(
                UUID.randomUUID(),
                customerId,
                request.contactEmail(),
                screeningId,
                BookingStatus.HELD,
                createdAt,
                createdAt.plusMinutes(10),
                new BigDecimal("29.90"),
                List.of(new BookingSeatResponse(
                                UUID.randomUUID(),
                                seatId,
                                new BigDecimal("29.90"))));
    }
}
