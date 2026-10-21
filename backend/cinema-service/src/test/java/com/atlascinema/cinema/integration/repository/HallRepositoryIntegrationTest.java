package com.atlascinema.cinema.integration.repository;

import com.atlascinema.cinema.domain.Cinema;
import com.atlascinema.cinema.domain.Hall;
import com.atlascinema.cinema.integration.BaseTestEnvironment;
import com.atlascinema.cinema.repository.CinemaRepository;
import com.atlascinema.cinema.repository.HallRepository;
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
class HallRepositoryIntegrationTest extends BaseTestEnvironment {

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findByIdAndCinema_Id_shouldReturnHall_whenHallBelongsToCinema() {
        Cinema cinema = createCinema("Atlas Cinema");
        Hall hall = cinema.addHall("Sala 1");

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        Hall found = hallRepository.findByIdAndCinema_Id(
                hall.getId(), cinema.getId()
        ).orElseThrow();

        assertThat(found.getId()).isEqualTo(hall.getId());
        assertThat(found.getName()).isEqualTo("Sala 1");
        assertThat(found.getCinema().getId())
                .isEqualTo(cinema.getId());
    }

    @Test
    void findByIdAndCinema_Id_shouldReturnEmpty_whenHallBelongsToAnotherCinema() {
        Cinema firstCinema = createCinema("Atlas Cinema Katowice");
        Cinema secondCinema = createCinema("Atlas Cinema Kraków");

        Hall hall = firstCinema.addHall("Sala 1");

        cinemaRepository.saveAllAndFlush(
                List.of(firstCinema, secondCinema)
        );
        entityManager.clear();

        assertThat(hallRepository.findByIdAndCinema_Id(
                hall.getId(), secondCinema.getId()
        )).isEmpty();
    }

    @Test
    void findAllByCinema_Id_shouldReturnOnlyHallsBelongingToCinema() {
        Cinema firstCinema = createCinema("Atlas Cinema Katowice");
        firstCinema.addHall("Sala 1");
        firstCinema.addHall("Sala 2");

        Cinema secondCinema = createCinema("Atlas Cinema Kraków");
        secondCinema.addHall("Sala VIP");

        cinemaRepository.saveAllAndFlush(
                List.of(firstCinema, secondCinema)
        );
        entityManager.clear();

        List<Hall> halls = hallRepository.findAllByCinema_Id(
                firstCinema.getId());

        assertThat(halls)
                .hasSize(2)
                .extracting(Hall::getName)
                .containsExactlyInAnyOrder("Sala 1", "Sala 2");

        assertThat(halls)
                .allSatisfy(hall ->
                        assertThat(hall.getCinema().getId())
                                .isEqualTo(firstCinema.getId()));
    }

    @Test
    void findAllByCinema_Id_shouldReturnEmptyList_whenCinemaHasNoHalls() {
        Cinema cinema = createCinema("Atlas Cinema");

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        List<Hall> halls = hallRepository.findAllByCinema_Id(
                cinema.getId()
        );

        assertThat(halls).isEmpty();
    }

    @Test
    void save_shouldRejectDuplicateHallName_whenHallsBelongToSameCinema() {
        Cinema cinema = createCinema("Atlas Cinema");

        cinema.addHall("Sala 1");
        cinema.addHall("Sala 1");

        assertThatThrownBy(() ->
                cinemaRepository.saveAndFlush(cinema)
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void save_shouldAllowSameHallName_whenHallsBelongToDifferentCinemas() {
        Cinema firstCinema = createCinema("Atlas Cinema Katowice");
        Cinema secondCinema = createCinema("Atlas Cinema Kraków");

        firstCinema.addHall("Sala 1");
        secondCinema.addHall("Sala 1");

        cinemaRepository.saveAllAndFlush(
                List.of(firstCinema, secondCinema));
        entityManager.clear();

        List<Hall> firstCinemaHalls = hallRepository.findAllByCinema_Id(
                firstCinema.getId());

        List<Hall> secondCinemaHalls = hallRepository.findAllByCinema_Id(
                secondCinema.getId());

        assertThat(firstCinemaHalls)
                .singleElement()
                .extracting(Hall::getName)
                .isEqualTo("Sala 1");

        assertThat(secondCinemaHalls)
                .singleElement()
                .extracting(Hall::getName)
                .isEqualTo("Sala 1");
    }

    private Cinema createCinema(String name) {
        return Cinema.create(
                name,
                "Katowice",
                "ul. Kinowa 10");
    }
}
