package com.atlascinema.booking.service;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.event.BookingExpiredEvent;
import com.atlascinema.booking.outbox.OutboxEventWriter;
import com.atlascinema.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Marks expired held bookings as {@link BookingStatus#EXPIRED}.
 *
 * <p>Redis releases temporary seat holds through TTL,
 * while this scheduler keeps the persistent booking state in sync
 * and creates BookingExpired events for downstream services.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpirationScheduler {

    private final BookingRepository bookingRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${booking.expiration-check-interval:30s}")
    @Transactional
    public void expireBookings() {
        Instant bookingExpirationOccurredAt = clock.instant();

        LocalDateTime bookingExpirationDateTime = LocalDateTime.ofInstant(
                        bookingExpirationOccurredAt,
                        clock.getZone());

        List<Booking> bookingsEligibleForExpiration = bookingRepository.findBookingsEligibleForExpiration(
                        BookingStatus.HELD,
                        bookingExpirationDateTime);

        for (Booking bookingToExpire : bookingsEligibleForExpiration) {
            bookingToExpire.markAsExpired();

            BookingExpiredEvent bookingExpiredEvent = new BookingExpiredEvent(
                            UUID.randomUUID(),
                            bookingToExpire.getId(),
                            bookingToExpire.getCustomerId(),
                            bookingToExpire.getContactEmail(),
                            bookingToExpire.getScreeningId(),
                            bookingExpirationOccurredAt);

            outboxEventWriter.saveExpired(bookingExpiredEvent, bookingExpirationDateTime);
        }

        if (!bookingsEligibleForExpiration.isEmpty()) {
            log.info("Expired {} bookings",
                    bookingsEligibleForExpiration.size());
        }
    }
}