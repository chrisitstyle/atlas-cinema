package com.atlascinema.cinema.integration.repository;

import com.atlascinema.cinema.domain.Cinema;
import com.atlascinema.cinema.domain.Hall;
import com.atlascinema.cinema.integration.BaseTestEnvironment;
import com.atlascinema.cinema.repository.CinemaRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class CinemaRepositoryIntegrationTest extends BaseTestEnvironment {

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void save_shouldPersistCinema_whenDataIsValid() {
        Cinema cinema = createCinema("Atlas Cinema", "Katowice");

        Cinema saved = cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        Cinema found = cinemaRepository.findById(saved.getId())
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getName()).isEqualTo("Atlas Cinema");
        assertThat(found.getCity()).isEqualTo("Katowice");
        assertThat(found.getAddress()).isEqualTo("ul. Kinowa 10");
        assertThat(found.isActive()).isTrue();
    }

    @Test
    void findById_shouldReturnEmpty_whenCinemaDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        assertThat(cinemaRepository.findById(unknownId))
                .isEmpty();
    }

    @Test
    void findAll_shouldReturnAllCinemas_whenCinemasExist() {
        Cinema firstCinema = createCinema(
                "Atlas Cinema Katowice", "Katowice");

        Cinema secondCinema = createCinema(
                "Atlas Cinema Kraków", "Kraków");

        cinemaRepository.saveAllAndFlush(
                List.of(firstCinema, secondCinema)
        );
        entityManager.clear();

        List<Cinema> cinemas = cinemaRepository.findAll();

        assertThat(cinemas)
                .hasSize(2)
                .extracting(Cinema::getName)
                .containsExactlyInAnyOrder(
                        "Atlas Cinema Katowice",
                        "Atlas Cinema Kraków"
                );
    }

    @Test
    void findWithHallsById_shouldFetchHalls_whenCinemaHasHalls() {
        Cinema cinema = createCinema("Atlas Cinema", "Katowice");

        cinema.addHall("Sala 1");
        cinema.addHall("Sala 2");

        cinemaRepository.saveAndFlush(cinema);
        entityManager.clear();

        Cinema found = cinemaRepository
                .findWithHallsById(cinema.getId())
                .orElseThrow();

        boolean hallsLoaded = entityManager
                .getEntityManagerFactory()
                .getPersistenceUnitUtil()
                .isLoaded(found, "halls");

        assertThat(hallsLoaded).isTrue();

        assertThat(found.getHalls())
                .hasSize(2)
                .extracting(Hall::getName)
                .containsExactlyInAnyOrder("Sala 1", "Sala 2");

        assertThat(found.getHalls())
                .allSatisfy(hall ->
                        assertThat(hall.getCinema().getId())
                                .isEqualTo(cinema.getId()));
    }

    @Test
    void findWithHallsById_shouldReturnEmpty_whenCinemaDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        assertThat(cinemaRepository.findWithHallsById(unknownId))
                .isEmpty();
    }

    private Cinema createCinema(String name, String city) {
        return Cinema.create(
                name,
                city,
                "ul. Kinowa 10");
    }
}
