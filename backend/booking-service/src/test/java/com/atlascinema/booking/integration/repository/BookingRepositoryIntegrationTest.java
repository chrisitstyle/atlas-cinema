package com.atlascinema.booking.integration.repository;

import com.atlascinema.booking.domain.Booking;
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
class BookingRepositoryIntegrationTest
        extends BaseTestEnvironment {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_shouldPersistBookingWithSeats_whenBookingIsValid() {
        Booking booking = Booking.create(
                UUID.randomUUID(),
                "john@example.com",
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 12, 0));

        booking.addSeat(UUID.randomUUID(),
                new BigDecimal("29.90"));

        booking.addSeat(UUID.randomUUID(),
                new BigDecimal("39.90"));

        Booking saved = bookingRepository.saveAndFlush(booking);

        entityManager.clear();

        Booking found = bookingRepository
                .findById(saved.getId())
                .orElseThrow();

        assertThat(found.getId())
                .isEqualTo(saved.getId());

        assertThat(found.getSeats())
                .hasSize(2);

        assertThat(found.getTotalPrice())
                .isEqualByComparingTo("69.80");
    }

    @Test
    void save_shouldThrowDataIntegrityViolationException_whenSeatIsAlreadyReservedForScreening() {
        UUID screeningId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        Booking firstBooking = Booking.create(
                UUID.randomUUID(),
                "first@example.com",
                screeningId,
                LocalDateTime.of(2030, 1, 10, 12, 0));

        firstBooking.addSeat(seatId, new BigDecimal("29.90"));

        bookingRepository.saveAndFlush(firstBooking);

        entityManager.clear();

        Booking secondBooking = Booking.create(
                UUID.randomUUID(),
                "second@example.com",
                screeningId,
                LocalDateTime.of(2030, 1, 10, 12, 1));

        secondBooking.addSeat(seatId, new BigDecimal("29.90"));

        assertThatThrownBy(() -> bookingRepository.saveAndFlush(secondBooking))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_shouldAllowSameSeat_whenScreeningIsDifferent() {
        UUID seatId = UUID.randomUUID();

        Booking firstBooking = Booking.create(
                UUID.randomUUID(),
                "first@example.com",
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 12, 0));

        firstBooking.addSeat(seatId, new BigDecimal("29.90"));

        Booking secondBooking = Booking.create(
                UUID.randomUUID(),
                "second@example.com",
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 12, 1));

        secondBooking.addSeat(seatId, new BigDecimal("29.90"));

        bookingRepository.saveAndFlush(firstBooking);
        bookingRepository.saveAndFlush(secondBooking);

        assertThat(bookingRepository.count())
                .isEqualTo(2);
    }
}
