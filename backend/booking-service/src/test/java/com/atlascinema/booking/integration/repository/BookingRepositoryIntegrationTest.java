package com.atlascinema.booking.integration.repository;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingSeat;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.integration.BaseTestEnvironment;
import com.atlascinema.booking.repository.BookingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookingRepositoryIntegrationTest extends BaseTestEnvironment {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_shouldPersistBookingWithSeats_whenBookingIsValid() {
        UUID screeningId = UUID.randomUUID();

        LocalDateTime createdAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        LocalDateTime expiresAt = createdAt.plusMinutes(10);

        UUID firstSeatId = UUID.randomUUID();
        UUID secondSeatId = UUID.randomUUID();

        Booking booking = Booking.create(
                        UUID.randomUUID(),
                        "john@example.com",
                        screeningId,
                        createdAt);

        booking.addSeat(firstSeatId, new BigDecimal("29.90"));

        booking.addSeat(secondSeatId, new BigDecimal("39.90"));

        booking.markAsHeld(expiresAt);

        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        entityManager.clear();

        Booking foundBooking = bookingRepository
                        .findById(savedBooking.getId())
                        .orElseThrow();

        assertThat(foundBooking.getId()).isEqualTo(savedBooking.getId());

        assertThat(foundBooking.getStatus()).isEqualTo(BookingStatus.HELD);

        assertThat(foundBooking.getCreatedAt()).isEqualTo(createdAt);

        assertThat(foundBooking.getExpiresAt()).isEqualTo(expiresAt);

        assertThat(foundBooking.getSeats()).hasSize(2);

        assertThat(foundBooking.getTotalPrice()).isEqualByComparingTo("69.80");

        assertThat(foundBooking.getSeats())
                .extracting(BookingSeat::getSeatId)
                .containsExactlyInAnyOrder(
                        firstSeatId,
                        secondSeatId);
    }

    @Test
    void findBookingsEligibleForExpiration_shouldReturnHeldBooking_whenExpirationTimeHasPassed() {
        LocalDateTime expirationCutoff = LocalDateTime.of(2030, 1, 10, 12, 0);

        Booking expiredHeldBooking = Booking.create(
                        UUID.randomUUID(),
                        "expired@example.com",
                        UUID.randomUUID(),
                        expirationCutoff.minusMinutes(20));

        expiredHeldBooking.addSeat(UUID.randomUUID(),
                new BigDecimal("29.90"));

        expiredHeldBooking.markAsHeld(expirationCutoff.minusMinutes(1));

        Booking activeHeldBooking = Booking.create(
                        UUID.randomUUID(),
                        "active@example.com",
                        UUID.randomUUID(),
                        expirationCutoff.minusMinutes(5));

        activeHeldBooking.addSeat(UUID.randomUUID(), new BigDecimal("29.90"));

        activeHeldBooking.markAsHeld(expirationCutoff.plusMinutes(5));

        bookingRepository.saveAllAndFlush(List.of(
                        expiredHeldBooking,
                        activeHeldBooking));

        entityManager.clear();

        List<Booking> bookingsEligibleForExpiration = bookingRepository.findBookingsEligibleForExpiration(
                        BookingStatus.HELD,
                        expirationCutoff);

        assertThat(bookingsEligibleForExpiration).hasSize(1);

        assertThat(bookingsEligibleForExpiration.getFirst().getId()).isEqualTo(expiredHeldBooking.getId());

        assertThat(bookingsEligibleForExpiration.getFirst().getStatus()).isEqualTo(BookingStatus.HELD);
    }

    @Test
    void findBookingsEligibleForExpiration_shouldNotReturnBooking_whenBookingIsNotHeld() {
        LocalDateTime expirationCutoff = LocalDateTime.of(2030, 1, 10, 12,0);

        Booking cancelledBooking = Booking.create(
                        UUID.randomUUID(),
                        "cancelled@example.com",
                        UUID.randomUUID(),
                        expirationCutoff.minusMinutes(20));

        cancelledBooking.addSeat(UUID.randomUUID(), new BigDecimal("29.90"));

        cancelledBooking.markAsHeld(expirationCutoff.minusMinutes(10));

        cancelledBooking.cancel(expirationCutoff.minusMinutes(5),
                expirationCutoff.plusHours(1));

        bookingRepository.saveAndFlush(cancelledBooking);

        entityManager.clear();

        List<Booking> bookingsEligibleForExpiration = bookingRepository.findBookingsEligibleForExpiration(
                        BookingStatus.HELD,
                        expirationCutoff);

        assertThat(bookingsEligibleForExpiration).isEmpty();
    }
}