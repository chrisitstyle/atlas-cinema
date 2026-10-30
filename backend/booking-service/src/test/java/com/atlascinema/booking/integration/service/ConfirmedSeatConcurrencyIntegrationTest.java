package com.atlascinema.booking.integration.service;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.ConfirmedSeat;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.atlascinema.booking.repository.BookingRepository;
import com.atlascinema.booking.repository.ConfirmedSeatRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Verifies that PostgreSQL never assigns one screening seat to two bookings
 */
class ConfirmedSeatConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ConfirmedSeatRepository confirmedSeatRepository;

    @Test
    void saveConfirmedSeat_shouldAllowOnlyOneSale_whenTwoTransactionsCompete()
            throws Exception {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();
        LocalDateTime confirmedAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        Booking firstBooking = bookingRepository.saveAndFlush(
                Booking.create(null, "first@example.com", screeningId, confirmedAt));
        Booking secondBooking = bookingRepository.saveAndFlush(
                Booking.create(null, "second@example.com", screeningId, confirmedAt));

        CountDownLatch bothThreadsReady = new CountDownLatch(2);
        CountDownLatch startBothThreads = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> firstSale = executor.submit(() -> attemptSale(
                    firstBooking.getId(), screeningId, seatId, confirmedAt,
                    bothThreadsReady, startBothThreads
            ));
            Future<Boolean> secondSale = executor.submit(() -> attemptSale(
                    secondBooking.getId(), screeningId, seatId, confirmedAt,
                    bothThreadsReady, startBothThreads));

            assertThat(bothThreadsReady.await(10, TimeUnit.SECONDS)).isTrue();
            startBothThreads.countDown();

            boolean firstSucceeded = firstSale.get(20, TimeUnit.SECONDS);
            boolean secondSucceeded = secondSale.get(20, TimeUnit.SECONDS);

            assertThat(firstSucceeded).isNotEqualTo(secondSucceeded);
            assertThat(confirmedSeatRepository.count()).isEqualTo(1);
        } finally {
            startBothThreads.countDown();
        }
    }

    private boolean attemptSale(
            UUID bookingId,
            UUID screeningId,
            UUID seatId,
            LocalDateTime confirmedAt,
            CountDownLatch bothThreadsReady,
            CountDownLatch startBothThreads
    ) throws InterruptedException {
        bothThreadsReady.countDown();

        if (!startBothThreads.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent seat sale did not start");
        }

        try {
            confirmedSeatRepository.saveAndFlush(ConfirmedSeat.create(
                    bookingId, screeningId, seatId, confirmedAt
            ));
            return true;
        } catch (DataIntegrityViolationException alreadySold) {
            return false;
        }
    }
}
