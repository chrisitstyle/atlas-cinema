package com.atlascinema.booking.service;

import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * Marks expired held bookings as {@link BookingStatus#EXPIRED}.
 *
 * <p>Redis is responsible for releasing temporary seat holds through TTL,
 * while this scheduler keeps the persistent booking status in sync.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingExpirationScheduler {

    private final BookingRepository bookingRepository;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${booking.expiration-check-interval:30s}")
    @Transactional
    public void expireBookings() {
        LocalDateTime now = LocalDateTime.now(clock);

        int expiredBookings = bookingRepository.expireBookings(
                        now,
                        BookingStatus.HELD,
                        BookingStatus.EXPIRED);

        if (expiredBookings > 0) {
            log.info("Expired {} bookings",
                    expiredBookings);
        }
    }
}
