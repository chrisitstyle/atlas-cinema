package com.atlascinema.cinema.integration.repository;

import com.atlascinema.cinema.domain.Cinema;
import com.atlascinema.cinema.domain.Hall;
import com.atlascinema.cinema.domain.Seat;
import com.atlascinema.cinema.domain.SeatType;
import com.atlascinema.cinema.integration.BaseTestEnvironment;
import com.atlascinema.cinema.repository.CinemaRepository;
import com.atlascinema.cinema.repository.SeatRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class SeatRepositoryIntegrationTest extends BaseTestEnvironment {

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_shouldPersistSeat_whenDataIsValid() {
        Cinema cinema = createCinema("Atlas Cinema");
        Hall hall = cinema.addHall("Sala 1");
        Seat seat = hall.addSeat("A", 1, SeatType.STANDARD);

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        Seat found = seatRepository.findById(seat.getId())
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(seat.getId());
        assertThat(found.getRow()).isEqualTo("A");
        assertThat(found.getNumber()).isEqualTo(1);
        assertThat(found.getType()).isEqualTo(SeatType.STANDARD);
        assertThat(found.getHall().getId()).isEqualTo(hall.getId());
    }

    @Test
    void existsByHall_IdAndRowAndNumber_shouldReturnTrue_whenSeatExists() {
        Cinema cinema = createCinema("Atlas Cinema");
        Hall hall = cinema.addHall("Sala 1");
        hall.addSeat("A", 1, SeatType.STANDARD);

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        boolean exists = seatRepository.existsByHall_IdAndRowAndNumber(
                hall.getId(), "A", 1);

        assertThat(exists).isTrue();
    }

    @Test
    void existsByHall_IdAndRowAndNumber_shouldReturnFalse_whenSeatDoesNotExist() {
        Cinema cinema = createCinema("Atlas Cinema");
        Hall hall = cinema.addHall("Sala 1");

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        boolean exists = seatRepository.existsByHall_IdAndRowAndNumber(
                hall.getId(), "A", 1);

        assertThat(exists).isFalse();
    }

    @Test
    void findAllByHall_Id_shouldReturnOnlySeatsBelongingToHall() {
        Cinema cinema = createCinema("Atlas Cinema");

        Hall firstHall = cinema.addHall("Sala 1");
        firstHall.addSeat("A", 1, SeatType.STANDARD);
        firstHall.addSeat("A", 2, SeatType.PREMIUM);

        Hall secondHall = cinema.addHall("Sala 2");
        secondHall.addSeat("B", 1, SeatType.VIP);

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        List<Seat> seats = seatRepository.findAllByHall_Id(
                firstHall.getId());

        assertThat(seats)
                .hasSize(2)
                .extracting(Seat::getNumber)
                .containsExactlyInAnyOrder(1, 2);

        assertThat(seats)
                .extracting(Seat::getType)
                .containsExactlyInAnyOrder(
                        SeatType.STANDARD,
                        SeatType.PREMIUM);

        assertThat(seats)
                .allSatisfy(seat ->
                        assertThat(seat.getHall().getId())
                                .isEqualTo(firstHall.getId()));
    }

    @Test
    void findByIdAndHall_IdAndHall_Cinema_Id_shouldReturnSeat_whenSeatBelongsToHallAndCinema() {
        Cinema cinema = createCinema("Atlas Cinema");
        Hall hall = cinema.addHall("Sala 1");
        Seat seat = hall.addSeat("A", 1, SeatType.VIP);

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        Seat found = seatRepository
                .findByIdAndHall_IdAndHall_Cinema_Id(
                        seat.getId(),
                        hall.getId(),
                        cinema.getId())
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(seat.getId());
        assertThat(found.getRow()).isEqualTo("A");
        assertThat(found.getNumber()).isEqualTo(1);
        assertThat(found.getType()).isEqualTo(SeatType.VIP);
    }

    @Test
    void findByIdAndHall_IdAndHall_Cinema_Id_shouldReturnEmpty_whenSeatBelongsToAnotherHall() {
        Cinema cinema = createCinema("Atlas Cinema");

        Hall firstHall = cinema.addHall("Sala 1");
        Seat seat = firstHall.addSeat("A", 1, SeatType.STANDARD);

        Hall secondHall = cinema.addHall("Sala 2");

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        assertThat(seatRepository.findByIdAndHall_IdAndHall_Cinema_Id(
                seat.getId(),
                secondHall.getId(),
                cinema.getId()
        )).isEmpty();
    }

    @Test
    void findByIdAndHall_IdAndHall_Cinema_Id_shouldReturnEmpty_whenSeatBelongsToAnotherCinema() {
        Cinema firstCinema = createCinema("Atlas Cinema Katowice");
        Hall hall = firstCinema.addHall("Sala 1");
        Seat seat = hall.addSeat("A", 1, SeatType.STANDARD);

        Cinema secondCinema = createCinema("Atlas Cinema Kraków");

        cinemaRepository.saveAllAndFlush(
                List.of(firstCinema, secondCinema)
        );
        entityManager.clear();

        assertThat(seatRepository.findByIdAndHall_IdAndHall_Cinema_Id(
                seat.getId(),
                hall.getId(),
                secondCinema.getId()
        )).isEmpty();
    }

    @Test
    void save_shouldRejectDuplicateSeatCoordinates_whenSeatsBelongToSameHall() {
        Cinema cinema = createCinema("Atlas Cinema");
        Hall hall = cinema.addHall("Sala 1");

        hall.addSeat("A", 1, SeatType.STANDARD);
        hall.addSeat("A", 1, SeatType.VIP);

        assertThatThrownBy(() -> cinemaRepository.saveAndFlush(cinema)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_shouldAllowSameSeatCoordinates_whenSeatsBelongToDifferentHalls() {
        Cinema cinema = createCinema("Atlas Cinema");

        Hall firstHall = cinema.addHall("Sala 1");
        firstHall.addSeat("A", 1, SeatType.STANDARD);

        Hall secondHall = cinema.addHall("Sala 2");
        secondHall.addSeat("A", 1, SeatType.VIP);

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        List<Seat> firstHallSeats = seatRepository.findAllByHall_Id(
                firstHall.getId());

        List<Seat> secondHallSeats = seatRepository.findAllByHall_Id(
                secondHall.getId());

        assertThat(firstHallSeats)
                .singleElement()
                .satisfies(seat -> {
                    assertThat(seat.getRow()).isEqualTo("A");
                    assertThat(seat.getNumber()).isEqualTo(1);
                    assertThat(seat.getType())
                            .isEqualTo(SeatType.STANDARD);
                });

        assertThat(secondHallSeats)
                .singleElement()
                .satisfies(seat -> {
                    assertThat(seat.getRow()).isEqualTo("A");
                    assertThat(seat.getNumber()).isEqualTo(1);
                    assertThat(seat.getType())
                            .isEqualTo(SeatType.VIP);
                });
    }

    @Test
    void save_shouldRejectSeat_whenSeatNumberIsNotPositive() {
        Cinema cinema = createCinema("Atlas Cinema");
        Hall hall = cinema.addHall("Sala 1");

        hall.addSeat("A", 0, SeatType.STANDARD);

        assertThatThrownBy(() ->
                cinemaRepository.saveAndFlush(cinema))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Cinema createCinema(String name) {
        return Cinema.create(
                name,
                "Katowice",
                "ul. Kinowa 10");
    }
}
