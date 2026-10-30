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
import java.util.Objects;
import java.util.UUID;

/*
 * Atomically confirms a paid booking and permanently assigns its seats
 * Records the payment responsible for confirming the booking
 */
@Service
@RequiredArgsConstructor
public class BookingConfirmationService {

    private final BookingRepository bookingRepository;
    private final ConfirmedSeatRepository confirmedSeatRepository;
    private final RedisSeatHoldService redisSeatHoldService;
    private final Clock clock;

    @Transactional
    public BookingResponse confirmPaidBooking(
            UUID bookingId,
            UUID paymentId
    ) {
        Objects.requireNonNull(bookingId, "Booking id is required");
        Objects.requireNonNull(paymentId, "Payment id is required");

        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        LocalDateTime confirmedAt = LocalDateTime.now(clock);

        // validate booking status and deadline before allocating seats
        booking.markAsConfirmed(confirmedAt, paymentId);

        List<UUID> seatIds = booking.getSeats().stream()
                .map(BookingSeat::getSeatId)
                .toList();

        // expired or transferred Redis holds cannot be converted into sales
        if (!redisSeatHoldService.areSeatsHeldByBooking(
                booking.getScreeningId(),
                seatIds,
                bookingId
        )) {
            throw new IllegalStateException(
                    "Booking %s no longer owns all Redis seat holds"
                            .formatted(bookingId));
        }

        List<ConfirmedSeat> permanentSeats = seatIds.stream()
                .map(seatId -> ConfirmedSeat.create(
                        bookingId,
                        booking.getScreeningId(),
                        seatId,
                        confirmedAt))
                .toList();

        // PostgreSQL unique constraints protect permanent seat ownership
        confirmedSeatRepository.saveAllAndFlush(permanentSeats);

        return BookingResponse.from(booking);
    }
}