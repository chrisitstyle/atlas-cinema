package com.atlascinema.movie.integration.repository;

import com.atlascinema.movie.domain.AgeRating;
import com.atlascinema.movie.domain.Genre;
import com.atlascinema.movie.domain.Movie;
import com.atlascinema.movie.integration.BaseTestEnvironment;
import com.atlascinema.movie.repository.MovieRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
class MovieRepositoryIntegrationTest extends BaseTestEnvironment {

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void shouldSaveAndFindMovieById() {
        Movie movie = createMovie("Interstellar");

        Movie saved = movieRepository.saveAndFlush(movie);
        entityManager.clear();

        Movie found = movieRepository.findById(saved.getId())
                .orElseThrow();

        assertThat(found.getId()).isEqualTo(saved.getId());
        assertThat(found.getTitle()).isEqualTo("Interstellar");
        assertThat(found.getDurationMinutes()).isEqualTo(169);
        assertThat(found.getGenre()).isEqualTo(Genre.SCIENCE_FICTION);
        assertThat(found.isDiscontinued()).isFalse();
    }

    @Test
    void findAll_shouldFindAllMovies() {
        Movie first = createMovie("Interstellar");
        Movie second = createMovie("Dune");

        movieRepository.saveAllAndFlush(List.of(first, second));
        entityManager.clear();

        List<Movie> movies = movieRepository.findAll();

        assertThat(movies)
                .hasSize(2)
                .extracting(Movie::getTitle)
                .containsExactlyInAnyOrder("Interstellar", "Dune");
    }

    @Test
    void shouldPersistDiscontinuedStatus() {
        Movie movie = createMovie("Interstellar");

        Movie saved = movieRepository.saveAndFlush(movie);

        saved.discontinue();
        entityManager.flush();
        entityManager.clear();

        Movie found = movieRepository.findById(saved.getId()).orElseThrow();

        assertThat(found.isDiscontinued()).isTrue();
    }

    private Movie createMovie(String title) {
        return Movie.create(
                title,
                "Sci-fi movie",
                169,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 11, 7),
                null,
                null);
    }
}
