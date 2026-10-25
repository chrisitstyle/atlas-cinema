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

        Booking saved = bookingRepository.saveAndFlush(booking);

        entityManager.clear();

        Booking found = bookingRepository
                .findById(saved.getId())
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());

        assertThat(found.getStatus()).isEqualTo(BookingStatus.HELD);

        assertThat(found.getCreatedAt()).isEqualTo(createdAt);

        assertThat(found.getExpiresAt()).isEqualTo(expiresAt);

        assertThat(found.getSeats()).hasSize(2);


        assertThat(found.getTotalPrice()).isEqualByComparingTo("69.80");

        assertThat(found.getSeats())
                .extracting(BookingSeat::getSeatId)
                .containsExactlyInAnyOrder(
                        firstSeatId,
                        secondSeatId);

    }

    @Test
    void expireBookings_shouldExpireHeldBookings_whenExpiresAtIsInThePast() {
        LocalDateTime now = LocalDateTime.of(2030, 1, 10, 12, 0);

        Booking expiredBooking = Booking.create(
                UUID.randomUUID(),
                "expired@example.com",
                UUID.randomUUID(),
                now.minusMinutes(20));

        expiredBooking.addSeat(UUID.randomUUID(), new BigDecimal("29.90"));

        expiredBooking.markAsHeld(now.minusMinutes(1));

        Booking activeBooking = Booking.create(
                UUID.randomUUID(),
                "active@example.com",
                UUID.randomUUID(),
                now.minusMinutes(5));

        activeBooking.addSeat(UUID.randomUUID(), new BigDecimal("29.90"));

        activeBooking.markAsHeld(now.plusMinutes(5));

        bookingRepository.saveAndFlush(expiredBooking);
        bookingRepository.saveAndFlush(activeBooking);

        int updated = bookingRepository.expireBookings(
                        now,
                        BookingStatus.HELD,
                        BookingStatus.EXPIRED);

        entityManager.clear();

        Booking foundExpiredBooking = bookingRepository
                        .findById(expiredBooking.getId())
                        .orElseThrow();

        Booking foundActiveBooking = bookingRepository
                        .findById(activeBooking.getId())
                        .orElseThrow();

        assertThat(updated).isEqualTo(1);

        assertThat(foundExpiredBooking.getStatus())
                .isEqualTo(BookingStatus.EXPIRED);

        assertThat(foundActiveBooking.getStatus())
                .isEqualTo(BookingStatus.HELD);
    }

    @Test
    void expireBookings_shouldNotExpireBooking_whenBookingIsNotHeld() {
        LocalDateTime now = LocalDateTime.of(2030, 1, 10, 12, 0);

        Booking booking = Booking.create(
                UUID.randomUUID(),
                "cancelled@example.com",
                UUID.randomUUID(),
                now.minusMinutes(20));

        booking.addSeat(UUID.randomUUID(), new BigDecimal("29.90"));

        booking.markAsHeld(now.minusMinutes(10));

        booking.cancel(now.minusMinutes(5), now.plusHours(1));

        bookingRepository.saveAndFlush(booking);

        int updated = bookingRepository.expireBookings(
                        now,
                        BookingStatus.HELD,
                        BookingStatus.EXPIRED);

        entityManager.clear();

        Booking found = bookingRepository
                        .findById(booking.getId())
                        .orElseThrow();

        assertThat(updated)
                .isZero();

        assertThat(found.getStatus())
                .isEqualTo(BookingStatus.CANCELLED);
    }
}