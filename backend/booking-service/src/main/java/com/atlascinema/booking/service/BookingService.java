package com.atlascinema.booking.service;

import com.atlascinema.booking.client.CinemaClient;
import com.atlascinema.booking.client.ScreeningClient;
import com.atlascinema.booking.client.dto.ScreeningClientResponse;
import com.atlascinema.booking.client.dto.ScreeningClientStatus;
import com.atlascinema.booking.config.BookingProperties;
import com.atlascinema.booking.domain.BookingSeat;
import com.atlascinema.booking.dto.BookingResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.event.BookingCancelledEvent;
import com.atlascinema.booking.exception.BookingNotFoundException;
import com.atlascinema.booking.exception.ScreeningNotFoundException;
import com.atlascinema.booking.exception.ScreeningUnavailableException;
import com.atlascinema.booking.exception.SeatAlreadyReservedException;
import com.atlascinema.booking.exception.SeatNotFoundException;
import com.atlascinema.booking.outbox.OutboxEventWriter;
import com.atlascinema.booking.repository.BookingRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ScreeningClient screeningClient;
    private final CinemaClient cinemaClient;
    private final RedisSeatHoldService redisSeatHoldService;
    private final BookingProperties bookingProperties;
    private final Clock clock;
    private final OutboxEventWriter outboxEventWriter;

    @Transactional
    public BookingResponse createBooking(
            CreateBookingRequest request
    ) {
        Duration holdDuration = bookingProperties.holdDuration();
        LocalDateTime createdAt = LocalDateTime.now(clock);

        ScreeningClientResponse screening = getScreening(request.screeningId());

        ensureScreeningIsAvailable(screening, createdAt);

        Booking booking = Booking.create(
                request.customerId(),
                request.contactEmail(),
                request.screeningId(),
                createdAt);

        /*
         * Validate every seat before touching Redis
         * This avoids creating temporary holds for an invalid request
         */
        for (UUID seatId : request.seatIds()) {
            ensureSeatExists(screening.hallId(), seatId);

            booking.addSeat(seatId, screening.basePrice());
        }

        /*
         * The hold starts after validation is complete
         * Booking expiry and Redis TTL use the same duration
         */
        LocalDateTime expiresAt = createdAt.plus(holdDuration);

        List<UUID> acquiredSeatIds = new ArrayList<>();

        try {
            for (UUID seatId : request.seatIds()) {
                boolean acquired = redisSeatHoldService.holdSeat(
                                request.screeningId(),
                                seatId,
                                booking.getId(),
                                holdDuration);

                if (!acquired) {
                    throw new SeatAlreadyReservedException(request.screeningId());
                }

                acquiredSeatIds.add(seatId);
            }

            booking.markAsHeld(expiresAt);

            Booking saved = bookingRepository.saveAndFlush(booking);

            return BookingResponse.from(saved);

        } catch (RuntimeException exception) {

            /*
             * Redis is outside the PostgreSQL transaction
             * release every hold acquired before the failure
             */
            releaseSeats(
                    request.screeningId(),
                    booking.getId(),
                    acquiredSeatIds
            );

            throw exception;
        }
    }

    @Transactional
    public BookingResponse cancelBooking(UUID bookingId) {
        Booking booking = bookingRepository
                .findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        ScreeningClientResponse screening = getScreening(booking.getScreeningId());

        // the same moment in whole transaction
        Instant occurredAt = clock.instant();

        LocalDateTime now = LocalDateTime.ofInstant(occurredAt,clock.getZone());

        booking.cancel(now, screening.startsAt());

        BookingCancelledEvent bookingCancelledEvent = new BookingCancelledEvent(
                UUID.randomUUID(),
                booking.getId(),
                booking.getCustomerId(),
                booking.getContactEmail(),
                booking.getScreeningId(),
                occurredAt);

        // store the event in the same database transaction as the booking change
        outboxEventWriter.save(bookingCancelledEvent, now);

        /*
         * flush the database change before releasing Redis holds
         * this reduces the chance of releasing seats when the database update fails
         */
        bookingRepository.flush();

        List<UUID> seatIds = booking
                .getSeats()
                .stream()
                .map(BookingSeat::getSeatId)
                .toList();

        releaseSeats(
                booking.getScreeningId(),
                booking.getId(),
                seatIds);

        return BookingResponse.from(booking);
    }

    private void releaseSeats(
            UUID screeningId,
            UUID bookingId,
            List<UUID> seatIds
    ) {
        for (UUID seatId : seatIds) {
            try {
                redisSeatHoldService.releaseSeat(
                        screeningId,
                        seatId,
                        bookingId
                );
            } catch (RuntimeException exception) {

                /*
                 * do not hide the original operation failure because cleanup failed
                 * TTL will eventually remove a stale hold
                 */
                log.warn(
                        "Failed to release Redis hold for screeningId={}, seatId={}, bookingId={}",
                        screeningId,
                        seatId,
                        bookingId,
                        exception);
            }
        }
    }

    private ScreeningClientResponse getScreening(UUID screeningId) {
        try {
            return screeningClient.getScreening(screeningId);
        } catch (FeignException.NotFound _) {
            throw new ScreeningNotFoundException(screeningId);
        }
    }

    private void ensureScreeningIsAvailable(ScreeningClientResponse screening,
            LocalDateTime now) {
        if (screening.status() != ScreeningClientStatus.SCHEDULED) {
            throw new ScreeningUnavailableException(screening.id());
        }

        if (!screening.startsAt().isAfter(now)) {
            throw new ScreeningUnavailableException(screening.id());
        }
    }

    private void ensureSeatExists(UUID hallId, UUID seatId) {
        try {
            cinemaClient.getSeat(hallId, seatId);
        } catch (FeignException.NotFound _) {
            throw new SeatNotFoundException(seatId);
        }
    }
}