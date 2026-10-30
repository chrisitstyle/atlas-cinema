package com.atlascinema.booking.service;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingSeat;
import com.atlascinema.booking.domain.ConfirmedSeat;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.exception.BookingNotFoundException;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.repository.ConfirmedSeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/*
 * Atomically confirms a held booking and permanently assigns its seats
 * Must only be called after the payment event has been validated
 */
@Service
@RequiredArgsConstructor
public class BookingConfirmationService {

    private final BookingRepository bookingRepository;
    private final ConfirmedSeatRepository confirmedSeatRepository;
    private final RedisSeatHoldService redisSeatHoldService;
    private final Clock clock;

    @Transactional
    public BookingResponse confirmPaidBooking(UUID bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        LocalDateTime confirmedAt = LocalDateTime.now(clock);

        // check booking state and expiry before allocating permanent seats
        booking.markAsConfirmed(confirmedAt);

        List<UUID> seatIds = booking.getSeats().stream()
                .map(BookingSeat::getSeatId)
                .toList();

        // an expired or transferred Redis hold cannot be converted to a sale
        if (!redisSeatHoldService.areSeatsHeldByBooking(
                booking.getScreeningId(), seatIds, bookingId)) {
            throw new IllegalStateException(
                    "Booking %s no longer owns all Redis seat holds".formatted(bookingId));
        }

        List<ConfirmedSeat> permanentSeats = seatIds.stream()
                .map(seatId -> ConfirmedSeat.create(
                        bookingId,
                        booking.getScreeningId(),
                        seatId,
                        confirmedAt
                ))
                .toList();

        // A unique index on (screening_id, seat_id) is the final sales guard
        // An insert conflict rolls back both the permanent seats and CONFIRMED
        confirmedSeatRepository.saveAllAndFlush(permanentSeats);

        return BookingResponse.from(booking);
    }
}
