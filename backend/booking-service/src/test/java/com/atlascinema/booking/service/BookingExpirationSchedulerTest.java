package com.atlascinema.booking.service;

import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {

    @Mock
    private BookingRepository bookingRepository;

    private Clock clock;

    private BookingExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
                Instant.parse("2030-01-10T12:00:00Z"),
                ZoneOffset.UTC);

        scheduler = new BookingExpirationScheduler(bookingRepository, clock);
    }

    @Test
    void expireBookings_shouldExpireHeldBookings_usingCurrentTime() {
        LocalDateTime now = LocalDateTime.of(2030, 1, 10, 12, 0);

        scheduler.expireBookings();

        verify(bookingRepository)
                .expireBookings(now, BookingStatus.HELD, BookingStatus.EXPIRED);
    }
}
