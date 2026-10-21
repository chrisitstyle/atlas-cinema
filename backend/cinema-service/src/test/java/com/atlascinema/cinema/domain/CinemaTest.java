package com.atlascinema.cinema.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CinemaTest {

    private Cinema cinema;

    @BeforeEach
    void setUp() {
        cinema = Cinema.create(
                "Atlas Cinema",
                "Katowice",
                "ul. Kinowa 10");
    }

    @Test
    void create_shouldCreateActiveCinema_whenDataIsValid() {
        assertThat(cinema.getId()).isNotNull();
        assertThat(cinema.getName()).isEqualTo("Atlas Cinema");
        assertThat(cinema.getCity()).isEqualTo("Katowice");
        assertThat(cinema.getAddress()).isEqualTo("ul. Kinowa 10");
        assertThat(cinema.isActive()).isTrue();
        assertThat(cinema.getHalls()).isEmpty();
    }

    @Test
    void addHall_shouldAddHallToCinema_whenCinemaExists() {
        Hall hall = cinema.addHall("Sala 1");

        assertThat(hall.getId()).isNotNull();
        assertThat(hall.getName()).isEqualTo("Sala 1");
        assertThat(hall.getCinema()).isSameAs(cinema);

        assertThat(cinema.getHalls())
                .hasSize(1)
                .containsExactly(hall);
    }

    @Test
    void addHall_shouldAddMultipleHalls_whenNamesAreDifferent() {
        Hall firstHall = cinema.addHall("Sala 1");
        Hall secondHall = cinema.addHall("Sala 2");

        assertThat(cinema.getHalls())
                .hasSize(2)
                .containsExactly(firstHall, secondHall);

        assertThat(firstHall.getId())
                .isNotEqualTo(secondHall.getId());

        assertThat(firstHall.getCinema()).isSameAs(cinema);
        assertThat(secondHall.getCinema()).isSameAs(cinema);
    }
}
