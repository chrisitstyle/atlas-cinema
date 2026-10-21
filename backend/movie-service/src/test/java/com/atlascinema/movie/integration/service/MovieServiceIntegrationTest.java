package com.atlascinema.movie.integration.service;

import com.atlascinema.movie.domain.AgeRating;
import com.atlascinema.movie.domain.Genre;
import com.atlascinema.movie.domain.Movie;
import com.atlascinema.movie.dto.CreateMovieRequest;
import com.atlascinema.movie.dto.MovieResponse;
import com.atlascinema.movie.dto.UpdateMovieRequest;
import com.atlascinema.movie.exception.MovieAlreadyActiveException;
import com.atlascinema.movie.exception.MovieAlreadyDiscontinuedException;
import com.atlascinema.movie.exception.MovieNotFoundException;
import com.atlascinema.movie.integration.AbstractIntegrationTest;
import com.atlascinema.movie.repository.MovieRepository;
import com.atlascinema.movie.service.MovieService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MovieServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MovieService movieService;

    @Autowired
    private MovieRepository movieRepository;

    @Test
    void createMovie_shouldPersistMovie_whenRequestIsValid() {
        CreateMovieRequest request = new CreateMovieRequest(
                "Interstellar",
                "Sci-fi movie",
                169,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 11, 7),
                "https://test.com/poster.jpg",
                "https://test.com/trailer"
        );

        MovieResponse response = movieService.createMovie(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.title()).isEqualTo("Interstellar");
        assertThat(response.discontinued()).isFalse();

        Movie saved = movieRepository.findById(response.id())
                .orElseThrow();

        assertThat(saved.getTitle()).isEqualTo("Interstellar");
        assertThat(saved.getDescription()).isEqualTo("Sci-fi movie");
        assertThat(saved.getDurationMinutes()).isEqualTo(169);
        assertThat(saved.getAgeRating()).isEqualTo(AgeRating.PG_13);
        assertThat(saved.getGenre()).isEqualTo(Genre.SCIENCE_FICTION);
        assertThat(saved.getReleaseDate())
                .isEqualTo(LocalDate.of(2026, 11, 7));
        assertThat(saved.getPosterUrl())
                .isEqualTo("https://test.com/poster.jpg");
        assertThat(saved.getTrailerUrl())
                .isEqualTo("https://test.com/trailer");
        assertThat(saved.isDiscontinued()).isFalse();
    }

    @Test
    void getMovieById_shouldReturnMovie_whenMovieExists() {
        Movie movie = movieRepository.saveAndFlush(
                createMovie("Interstellar")
        );

        MovieResponse response = movieService.getMovieById(movie.getId());

        assertThat(response.id()).isEqualTo(movie.getId());
        assertThat(response.title()).isEqualTo("Interstellar");
        assertThat(response.durationMinutes()).isEqualTo(169);
        assertThat(response.genre()).isEqualTo(Genre.SCIENCE_FICTION);
    }

    @Test
    void getMovieById_shouldThrowMovieNotFoundException_whenMovieDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> movieService.getMovieById(unknownId))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void getAllMovies_shouldReturnAllMovies_whenMoviesExist() {
        movieRepository.saveAllAndFlush(List.of(
                createMovie("Interstellar"),
                createMovie("Dune")
        ));

        List<MovieResponse> response = movieService.getAllMovies();

        assertThat(response)
                .hasSize(2)
                .extracting(MovieResponse::title)
                .containsExactlyInAnyOrder("Interstellar", "Dune");
    }

    @Test
    void getAllMovies_shouldReturnEmptyList_whenNoMoviesExist() {
        List<MovieResponse> response = movieService.getAllMovies();

        assertThat(response).isEmpty();
    }

    @Test
    void updateMovie_shouldPersistUpdatedDetails_whenMovieExists() {
        Movie movie = movieRepository.saveAndFlush(
                createMovie("Interstellar")
        );

        UpdateMovieRequest request = new UpdateMovieRequest(
                "Updated movie",
                "Updated description",
                180,
                AgeRating.R,
                Genre.DRAMA,
                LocalDate.of(2026, 1, 1),
                "https://test.com/new-poster.jpg",
                "https://test.com/new-trailer"
        );

        MovieResponse response = movieService.updateMovie(
                movie.getId(), request
        );

        assertThat(response.title()).isEqualTo("Updated movie");
        assertThat(response.durationMinutes()).isEqualTo(180);
        assertThat(response.genre()).isEqualTo(Genre.DRAMA);

        Movie updated = movieRepository.findById(movie.getId())
                .orElseThrow();

        assertThat(updated.getTitle()).isEqualTo("Updated movie");
        assertThat(updated.getDescription())
                .isEqualTo("Updated description");
        assertThat(updated.getDurationMinutes()).isEqualTo(180);
        assertThat(updated.getAgeRating()).isEqualTo(AgeRating.R);
        assertThat(updated.getGenre()).isEqualTo(Genre.DRAMA);
        assertThat(updated.getReleaseDate())
                .isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(updated.getPosterUrl())
                .isEqualTo("https://test.com/new-poster.jpg");
        assertThat(updated.getTrailerUrl())
                .isEqualTo("https://test.com/new-trailer");
    }

    @Test
    void updateMovie_shouldThrowMovieNotFoundException_whenMovieDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        UpdateMovieRequest request = new UpdateMovieRequest(
                "Updated movie",
                "Updated description",
                180,
                AgeRating.R,
                Genre.DRAMA,
                LocalDate.of(2026, 1, 1),
                null,
                null
        );

        assertThatThrownBy(() -> movieService.updateMovie(unknownId, request))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void discontinueMovie_shouldPersistDiscontinuedStatus_whenMovieIsActive() {
        Movie movie = movieRepository.saveAndFlush(
                createMovie("Interstellar"));

        MovieResponse response = movieService.discontinueMovie(movie.getId());

        assertThat(response.discontinued()).isTrue();

        Movie updated = movieRepository.findById(movie.getId())
                .orElseThrow();

        assertThat(updated.isDiscontinued()).isTrue();
    }

    @Test
    void discontinueMovie_shouldThrowMovieNotFoundException_whenMovieDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> movieService.discontinueMovie(unknownId))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void discontinueMovie_shouldThrowMovieAlreadyDiscontinuedException_whenMovieAlreadyDiscontinued() {
        Movie movie = createMovie("Interstellar");
        movie.discontinue();
        movieRepository.saveAndFlush(movie);

        assertThatThrownBy(() -> movieService.discontinueMovie(movie.getId()))
                .isInstanceOf(MovieAlreadyDiscontinuedException.class)
                .hasMessageContaining(movie.getId().toString());
    }

    @Test
    void restoreMovie_shouldPersistActiveStatus_whenMovieIsDiscontinued() {
        Movie movie = createMovie("Interstellar");
        movie.discontinue();
        movieRepository.saveAndFlush(movie);

        MovieResponse response = movieService.restoreMovie(movie.getId());

        assertThat(response.discontinued()).isFalse();

        Movie restored = movieRepository.findById(movie.getId())
                .orElseThrow();

        assertThat(restored.isDiscontinued()).isFalse();
    }

    @Test
    void restoreMovie_shouldThrowMovieNotFoundException_whenMovieDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> movieService.restoreMovie(unknownId))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void restoreMovie_shouldThrowMovieAlreadyActiveException_whenMovieAlreadyActive() {
        Movie movie = movieRepository.saveAndFlush(
                createMovie("Interstellar")
        );

        assertThatThrownBy(() -> movieService.restoreMovie(movie.getId()))
                .isInstanceOf(MovieAlreadyActiveException.class)
                .hasMessageContaining(movie.getId().toString());
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
                null
        );
    }
}
