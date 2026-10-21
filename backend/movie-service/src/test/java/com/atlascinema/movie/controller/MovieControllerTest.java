package com.atlascinema.movie.controller;

import com.atlascinema.movie.domain.AgeRating;
import com.atlascinema.movie.domain.Genre;
import com.atlascinema.movie.dto.CreateMovieRequest;
import com.atlascinema.movie.dto.MovieResponse;
import com.atlascinema.movie.dto.UpdateMovieRequest;
import com.atlascinema.movie.service.MovieService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MovieControllerTest {

    @Mock
    private MovieService movieService;

    @InjectMocks
    private MovieController movieController;

    private UUID movieId;
    private MovieResponse movieResponse;
    private CreateMovieRequest createMovieRequest;
    private UpdateMovieRequest updateMovieRequest;

    @BeforeEach
    void setUp() {
        movieId = UUID.randomUUID();

        movieResponse = createMovieResponse(
                movieId, "Interstellar", false
        );

        createMovieRequest = new CreateMovieRequest(
                "Interstellar",
                "Sci-fi movie",
                169,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 11, 7),
                null,
                null
        );

        updateMovieRequest = new UpdateMovieRequest(
                "Updated movie",
                "Updated description",
                180,
                AgeRating.R,
                Genre.DRAMA,
                LocalDate.of(2026, 1, 1),
                null,
                null);
    }

    @Test
    void createMovie_shouldReturnCreatedMovie_whenRequestIsValid() {
        when(movieService.createMovie(createMovieRequest))
                .thenReturn(movieResponse);

        ResponseEntity<MovieResponse> response =
                movieController.createMovie(createMovieRequest);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/movies/" + movieId));

        assertThat(response.getBody())
                .isEqualTo(movieResponse);

        verify(movieService).createMovie(createMovieRequest);
    }

    @Test
    void getMovie_shouldReturnMovie_whenMovieExists() {
        when(movieService.getMovieById(movieId))
                .thenReturn(movieResponse);

        MovieResponse response = movieController.getMovie(movieId);

        assertThat(response).isEqualTo(movieResponse);
        assertThat(response.id()).isEqualTo(movieId);
        assertThat(response.title()).isEqualTo("Interstellar");

        verify(movieService).getMovieById(movieId);
    }

    @Test
    void getAllMovies_shouldReturnAllMovies_whenMoviesExist() {
        MovieResponse secondMovie = createMovieResponse(
                UUID.randomUUID(), "Dune", false);

        when(movieService.getAllMovies())
                .thenReturn(List.of(movieResponse, secondMovie));

        List<MovieResponse> response = movieController.getAllMovies();

        assertThat(response)
                .hasSize(2)
                .extracting(MovieResponse::title)
                .containsExactly("Interstellar", "Dune");

        verify(movieService).getAllMovies();
    }

    @Test
    void updateMovie_shouldReturnUpdatedMovie_whenMovieExists() {
        MovieResponse updatedMovie = createMovieResponse(
                movieId, "Updated movie", false);

        when(movieService.updateMovie(movieId, updateMovieRequest))
                .thenReturn(updatedMovie);

        MovieResponse response = movieController.updateMovie(
                movieId, updateMovieRequest);

        assertThat(response).isEqualTo(updatedMovie);
        assertThat(response.id()).isEqualTo(movieId);
        assertThat(response.title()).isEqualTo("Updated movie");

        verify(movieService).updateMovie(movieId, updateMovieRequest);
    }

    @Test
    void discontinueMovie_shouldReturnDiscontinuedMovie_whenMovieIsActive() {
        MovieResponse discontinuedMovie = createMovieResponse(
                movieId, "Interstellar", true);

        when(movieService.discontinueMovie(movieId))
                .thenReturn(discontinuedMovie);

        MovieResponse response =
                movieController.discontinueMovie(movieId);

        assertThat(response).isEqualTo(discontinuedMovie);
        assertThat(response.discontinued()).isTrue();

        verify(movieService).discontinueMovie(movieId);
    }

    @Test
    void restoreMovie_shouldReturnRestoredMovie_whenMovieIsDiscontinued() {
        MovieResponse restoredMovie = createMovieResponse(
                movieId, "Interstellar", false);

        when(movieService.restoreMovie(movieId))
                .thenReturn(restoredMovie);

        MovieResponse response = movieController.restoreMovie(movieId);

        assertThat(response).isEqualTo(restoredMovie);
        assertThat(response.discontinued()).isFalse();

        verify(movieService).restoreMovie(movieId);
    }

    private MovieResponse createMovieResponse(
            UUID id,
            String title,
            boolean discontinued
    ) {
        return new MovieResponse(
                id,
                title,
                "Sci-fi movie",
                169,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 11, 7),
                null,
                null,
                discontinued);
    }
}
