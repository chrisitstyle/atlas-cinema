package com.atlascinema.booking.service;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.event.BookingExpiredEvent;
import com.atlascinema.booking.outbox.OutboxEventWriter;
import com.atlascinema.booking.repository.BookingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingExpirationSchedulerTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    private static final LocalDateTime NOW_DATE_TIME = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private OutboxEventWriter outboxEventWriter;

    private BookingExpirationScheduler bookingExpirationScheduler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        bookingExpirationScheduler = new BookingExpirationScheduler(
                bookingRepository, outboxEventWriter, clock);
    }

    @Test
    void expireBookings_shouldExpireHeldBookingAndCreateOutboxEvent_whenBookingExpirationTimeHasPassed() {
        UUID customerId = UUID.randomUUID();
        UUID screeningId = UUID.randomUUID();

        Booking bookingToExpire = Booking.create(
                customerId,
                "guest@example.com",
                screeningId,
                NOW_DATE_TIME.minusMinutes(20));

        bookingToExpire.addSeat(UUID.randomUUID(), new BigDecimal("29.90"));

        bookingToExpire.markAsHeld(NOW_DATE_TIME.minusMinutes(1));

        when(bookingRepository.findBookingsEligibleForExpiration(
                BookingStatus.HELD, NOW_DATE_TIME))
                .thenReturn(List.of(bookingToExpire));

        bookingExpirationScheduler.expireBookings();

        assertThat(bookingToExpire.getStatus()).isEqualTo(BookingStatus.EXPIRED);

        ArgumentCaptor<BookingExpiredEvent> bookingExpiredEventCaptor = ArgumentCaptor
                .forClass(BookingExpiredEvent.class);

        verify(outboxEventWriter).saveExpired(bookingExpiredEventCaptor.capture(), eq(NOW_DATE_TIME));

        BookingExpiredEvent bookingExpiredEvent = bookingExpiredEventCaptor.getValue();

        assertThat(bookingExpiredEvent.eventId()).isNotNull();

        assertThat(bookingExpiredEvent.bookingId()).isEqualTo(bookingToExpire.getId());

        assertThat(bookingExpiredEvent.customerId()).isEqualTo(customerId);

        assertThat(bookingExpiredEvent.contactEmail()).isEqualTo("guest@example.com");

        assertThat(bookingExpiredEvent.screeningId()).isEqualTo(screeningId);

        assertThat(bookingExpiredEvent.occurredAt()).isEqualTo(NOW);
    }
}