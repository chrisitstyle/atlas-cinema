package com.atlascinema.movie.domain;

import com.atlascinema.movie.exception.MovieAlreadyActiveException;
import com.atlascinema.movie.exception.MovieAlreadyDiscontinuedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovieTest {

    private Movie movie;

    @BeforeEach
    void setUp() {
        movie = Movie.create(
                "Interstellar",
                "Sci-fi movie",
                169,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 11, 7),
                "https://test.com/poster.jpg",
                "https://test.com/trailer");
    }

    @Test
    void create_shouldCreateActiveMovie() {
        assertThat(movie.getId()).isNotNull();
        assertThat(movie.getTitle()).isEqualTo("Interstellar");
        assertThat(movie.getDurationMinutes()).isEqualTo(169);
        assertThat(movie.getGenre()).isEqualTo(Genre.SCIENCE_FICTION);
        assertThat(movie.isDiscontinued()).isFalse();
    }

    @Test
    void updateMovie_shouldUpdateMovieDetails_whenMovieExists() {
        movie.updateDetails(
                "Interstellar Updated",
                "Updated description",
                175,
                AgeRating.R,
                Genre.DRAMA,
                LocalDate.of(2026, 1, 1),
                "https://test.com/new-poster",
                "https://test.com/new-trailer");

        assertThat(movie.getTitle()).isEqualTo("Interstellar Updated");
        assertThat(movie.getDescription()).isEqualTo("Updated description");
        assertThat(movie.getDurationMinutes()).isEqualTo(175);
        assertThat(movie.getAgeRating()).isEqualTo(AgeRating.R);
        assertThat(movie.getGenre()).isEqualTo(Genre.DRAMA);
        assertThat(movie.getReleaseDate())
                .isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void discontinueMovie_shouldDiscontinueMovie_whenMovieIsActive() {
        movie.discontinue();
        assertThat(movie.isDiscontinued()).isTrue();
    }

    @Test
    void discontinue_shouldThrowWhenMovieAlreadyDiscontinued() {
        movie.discontinue();

        assertThatThrownBy(movie::discontinue)
                .isInstanceOf(MovieAlreadyDiscontinuedException.class)
                .hasMessageContaining(movie.getId().toString());
    }

    @Test
    void restore_shouldRestoreDiscontinuedMovie() {
        movie.discontinue();
        movie.restore();

        assertThat(movie.isDiscontinued()).isFalse();
    }

    @Test
    void restore_shouldThrowWhenMovieAlreadyActive() {
        assertThatThrownBy(movie::restore)
                .isInstanceOf(MovieAlreadyActiveException.class)
                .hasMessageContaining(movie.getId().toString());
    }
}
