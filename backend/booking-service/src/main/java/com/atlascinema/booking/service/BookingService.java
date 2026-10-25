package com.atlascinema.booking.service;

import com.atlascinema.booking.client.CinemaClient;
import com.atlascinema.booking.client.ScreeningClient;
import com.atlascinema.booking.client.dto.ScreeningClientResponse;
import com.atlascinema.booking.client.dto.ScreeningClientStatus;
import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.exception.*;
import com.atlascinema.booking.repository.BookingRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ScreeningClient screeningClient;
    private final CinemaClient cinemaClient;
    private final Clock clock;

    private static final Duration HOLD_DURATION = Duration.ofMinutes(10);

    @Transactional
    public BookingResponse createBooking(
            CreateBookingRequest request
    ) {
        LocalDateTime now = LocalDateTime.now(clock);

        LocalDateTime expiresAt = now.plus(HOLD_DURATION);

        ScreeningClientResponse screening = getScreening(request.screeningId());

        ensureScreeningIsAvailable(screening, now);

        Booking booking = Booking.create(
                        request.customerId(),
                        request.contactEmail(),
                        request.screeningId(),
                        now);

        for (UUID seatId : request.seatIds()) {

            ensureSeatExists(screening.hallId(), seatId);

            booking.holdSeat(seatId, screening.basePrice(), expiresAt);
        }

        booking.markAsHeld(expiresAt);

        try {
            Booking saved = bookingRepository.saveAndFlush(booking);

            return BookingResponse.from(saved);

        } catch (DataIntegrityViolationException exception) {
            throw new SeatAlreadyReservedException(
                    request.screeningId()
            );
        }
    }

    @Transactional
    public BookingResponse cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        ScreeningClientResponse screening = getScreening(booking.getScreeningId());

        LocalDateTime now = LocalDateTime.now(clock);

        booking.cancel(now, screening.startsAt());

        return BookingResponse.from(booking);
    }

    private ScreeningClientResponse getScreening(
            UUID screeningId
    ) {
        try {
            return screeningClient.getScreening(
                    screeningId
            );
        } catch (FeignException.NotFound exception) {
            throw new ScreeningNotFoundException(screeningId);
        }
    }

    private void ensureScreeningIsAvailable(
            ScreeningClientResponse screening,
            LocalDateTime now
    ) {
        if (screening.status()
                != ScreeningClientStatus.SCHEDULED) {

            throw new ScreeningUnavailableException(
                    screening.id()
            );
        }

        if (!screening.startsAt().isAfter(now)) {
            throw new ScreeningUnavailableException(
                    screening.id());
        }
    }

    private void ensureSeatExists(
            UUID hallId,
            UUID seatId
    ) {
        try {
            cinemaClient.getSeat(hallId, seatId);
        } catch (FeignException.NotFound exception) {
            throw new SeatNotFoundException(seatId);
        }
    }
}
