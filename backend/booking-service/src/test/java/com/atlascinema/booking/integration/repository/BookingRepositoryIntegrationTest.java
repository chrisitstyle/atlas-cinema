package com.atlascinema.booking.integration.repository;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingSeat;
import com.atlascinema.booking.domain.BookingStatus;
import com.atlascinema.booking.domain.SeatHold;
import com.atlascinema.booking.integration.BaseTestEnvironment;
import com.atlascinema.booking.repository.BookingRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class BookingRepositoryIntegrationTest extends BaseTestEnvironment {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_shouldPersistBookingWithSeatsAndHolds_whenBookingIsValid() {
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

        booking.holdSeat(
                firstSeatId,
                new BigDecimal("29.90"),
                expiresAt);

        booking.holdSeat(
                secondSeatId,
                new BigDecimal("39.90"),
                expiresAt);

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

        assertThat(found.getHolds()).hasSize(2);

        assertThat(found.getTotalPrice()).isEqualByComparingTo("69.80");

        assertThat(found.getSeats())
                .extracting(BookingSeat::getSeatId)
                .containsExactlyInAnyOrder(
                        firstSeatId,
                        secondSeatId);

        assertThat(found.getHolds())
                .allSatisfy(hold -> {
                    assertThat(hold.getScreeningId())
                            .isEqualTo(screeningId);

                    assertThat(hold.getExpiresAt())
                            .isEqualTo(expiresAt);
                });

        assertThat(found.getHolds())
                .extracting(SeatHold::getSeatId)
                .containsExactlyInAnyOrder(
                        firstSeatId,
                        secondSeatId);
    }

    @Test
    void save_shouldThrowDataIntegrityViolationException_whenSeatIsAlreadyHeldForScreening() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        LocalDateTime firstCreatedAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        LocalDateTime firstExpiresAt = firstCreatedAt.plusMinutes(10);

        Booking firstBooking = Booking.create(
                UUID.randomUUID(),
                "first@example.com",
                screeningId,
                firstCreatedAt);

        firstBooking.holdSeat(
                seatId,
                new BigDecimal("29.90"),
                firstExpiresAt);

        firstBooking.markAsHeld(firstExpiresAt);

        bookingRepository.saveAndFlush(firstBooking);

        entityManager.clear();

        LocalDateTime secondCreatedAt = LocalDateTime.of(2030, 1, 10, 12, 1);

        LocalDateTime secondExpiresAt = secondCreatedAt.plusMinutes(10);

        Booking secondBooking = Booking.create(
                UUID.randomUUID(),
                "second@example.com",
                screeningId,
                secondCreatedAt);

        secondBooking.holdSeat(
                seatId,
                new BigDecimal("29.90"),
                secondExpiresAt);

        secondBooking.markAsHeld(secondExpiresAt);

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(secondBooking))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_shouldAllowSameSeat_whenScreeningIsDifferent() {
        UUID seatId = UUID.randomUUID();

        UUID firstScreeningId = UUID.randomUUID();

        UUID secondScreeningId = UUID.randomUUID();

        LocalDateTime firstCreatedAt = LocalDateTime.of(2030, 1, 10, 12, 0);

        LocalDateTime firstExpiresAt = firstCreatedAt.plusMinutes(10);

        Booking firstBooking = Booking.create(
                UUID.randomUUID(),
                "first@example.com",
                firstScreeningId,
                firstCreatedAt);

        firstBooking.holdSeat(seatId,
                new BigDecimal("29.90"),
                firstExpiresAt);

        firstBooking.markAsHeld(firstExpiresAt);

        LocalDateTime secondCreatedAt = LocalDateTime.of(2030, 1, 10, 12, 1);

        LocalDateTime secondExpiresAt = secondCreatedAt.plusMinutes(10);

        Booking secondBooking = Booking.create(
                UUID.randomUUID(),
                "second@example.com",
                secondScreeningId,
                secondCreatedAt);

        secondBooking.holdSeat(seatId, new BigDecimal("29.90"), secondExpiresAt);

        secondBooking.markAsHeld(secondExpiresAt);

        bookingRepository.saveAndFlush(firstBooking);

        bookingRepository.saveAndFlush(secondBooking);

        assertThat(bookingRepository.count()).isEqualTo(2);
    }
}