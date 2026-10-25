package com.atlascinema.booking.controller;

import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.BookingSeatResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.service.BookingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private BookingController bookingController;

    @Test
    void createBooking_shouldReturnCreatedBooking_whenRequestIsValid() {
        UUID bookingId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID bookingSeatId = UUID.randomUUID();

        CreateBookingRequest request = new CreateBookingRequest(null, "guest@example.com",
                        screeningId,
                        List.of(seatId));

        BookingResponse response = new BookingResponse(
                        bookingId,
                        null,
                        "guest@example.com",
                        screeningId,
                        BookingStatus.PENDING,
                        LocalDateTime.of(2030, 1, 10, 12, 0),
                        null,
                        new BigDecimal("29.90"),
                        List.of(
                                new BookingSeatResponse(
                                        bookingSeatId,
                                        seatId,
                                        new BigDecimal("29.90"))));

        when(bookingService.createBooking(request))
                .thenReturn(response);

        ResponseEntity<BookingResponse> result = bookingController.createBooking(request);

        assertThat(result.getStatusCode().value()).isEqualTo(201);

        assertThat(result.getHeaders().getLocation()).hasToString("/bookings/" + bookingId);

        assertThat(result.getBody()).isEqualTo(response);

        verify(bookingService).createBooking(request);
    }

    @Test
    void cancelBooking_shouldReturnCancelledBooking_whenBookingCanBeCancelled() {
        UUID bookingId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        UUID bookingSeatId = UUID.randomUUID();

        LocalDateTime createdAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        LocalDateTime expiresAt = createdAt.plusMinutes(10);

        BookingResponse response = new BookingResponse(
                        bookingId,
                        null,
                        "guest@example.com",
                        screeningId,
                        BookingStatus.CANCELLED,
                        createdAt,
                        expiresAt,
                        new BigDecimal("29.90"),
                        List.of(
                                new BookingSeatResponse(
                                        bookingSeatId,
                                        seatId,
                                        new BigDecimal("29.90"))));

        when(bookingService.cancelBooking(bookingId)).thenReturn(response);

        BookingResponse result = bookingController.cancelBooking(bookingId);

        assertThat(result).isEqualTo(response);

        assertThat(result.status()).isEqualTo(BookingStatus.CANCELLED);

        verify(bookingService).cancelBooking(bookingId);
    }
}

