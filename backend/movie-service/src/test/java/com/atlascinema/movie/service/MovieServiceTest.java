package com.atlascinema.movie.service;

import com.atlascinema.movie.domain.AgeRating;
import com.atlascinema.movie.domain.Genre;
import com.atlascinema.movie.domain.Movie;
import com.atlascinema.movie.dto.CreateMovieRequest;
import com.atlascinema.movie.dto.MovieResponse;
import com.atlascinema.movie.dto.UpdateMovieRequest;
import com.atlascinema.movie.exception.MovieNotFoundException;
import com.atlascinema.movie.repository.MovieRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MovieServiceTest {

    @Mock
    private MovieRepository movieRepository;

    @InjectMocks
    private MovieService movieService;

    private Movie movie;
    private UUID movieId;

    @BeforeEach
    void setUp() {
        movie = Movie.create(
                "Interstellar",
                "Sci-fi movie",
                169,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 11, 7),
                null,
                null
        );

        movieId = movie.getId();
    }

    @Test
    void createMovie_shouldCreateMovie_whenRequestIsValid() {
        CreateMovieRequest request = new CreateMovieRequest(
                "Dune",
                "Sci-fi movie",
                155,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 10, 22),
                null,
                null);

        when(movieRepository.save(any(Movie.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MovieResponse result = movieService.createMovie(request);

        assertThat(result.id()).isNotNull();
        assertThat(result.title()).isEqualTo("Dune");
        assertThat(result.durationMinutes()).isEqualTo(155);
        assertThat(result.discontinued()).isFalse();

        verify(movieRepository).save(any(Movie.class));
    }

    @Test
    void getMovieById_shouldReturnMovie_whenMovieExists() {
        when(movieRepository.findById(movieId))
                .thenReturn(Optional.of(movie));

        MovieResponse result = movieService.getMovieById(movieId);

        assertThat(result.id()).isEqualTo(movieId);
        assertThat(result.title()).isEqualTo("Interstellar");

        verify(movieRepository).findById(movieId);
    }

    @Test
    void getMovieById_shouldThrowMovieNotFoundException_whenMovieDoesNotExist() {
        when(movieRepository.findById(movieId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.getMovieById(movieId))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining(movieId.toString());
    }

    @Test
    void getAllMovies_shouldReturnAllMovies_whenMoviesExist() {
        Movie secondMovie = Movie.create(
                "Dune",
                "Sci-fi movie",
                155,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 10, 22),
                null,
                null);

        when(movieRepository.findAll())
                .thenReturn(List.of(movie, secondMovie));

        List<MovieResponse> result = movieService.getAllMovies();

        assertThat(result)
                .hasSize(2)
                .extracting(MovieResponse::title)
                .containsExactly("Interstellar", "Dune");

        verify(movieRepository).findAll();
    }

    @Test
    void updateMovie_shouldUpdateMovieDetails_whenMovieExists() {
        UpdateMovieRequest request = new UpdateMovieRequest(
                "Updated title",
                "Updated description",
                180,
                AgeRating.R,
                Genre.DRAMA,
                LocalDate.of(2026, 1, 1),
                null,
                null);

        when(movieRepository.findById(movieId))
                .thenReturn(Optional.of(movie));

        MovieResponse result = movieService.updateMovie(movieId, request);

        assertThat(result.title()).isEqualTo("Updated title");
        assertThat(result.durationMinutes()).isEqualTo(180);
        assertThat(result.genre()).isEqualTo(Genre.DRAMA);

        assertThat(movie.getTitle()).isEqualTo("Updated title");

        verify(movieRepository).findById(movieId);
        verify(movieRepository, never()).save(any());
    }

    @Test
    void discontinueMovie_shouldDiscontinueMovie_whenMovieIsActive() {
        when(movieRepository.findById(movieId))
                .thenReturn(Optional.of(movie));

        MovieResponse result = movieService.discontinueMovie(movieId);

        assertThat(result.discontinued()).isTrue();
        assertThat(movie.isDiscontinued()).isTrue();
    }

    @Test
    void restoreMovie_shouldRestoreMovie_whenMovieIsDiscontinued() {
        movie.discontinue();

        when(movieRepository.findById(movieId))
                .thenReturn(Optional.of(movie));

        MovieResponse result = movieService.restoreMovie(movieId);

        assertThat(result.discontinued()).isFalse();
        assertThat(movie.isDiscontinued()).isFalse();
    }

    @Test
    void updateMovie_shouldThrowMovieNotFoundException_whenMovieDoesNotExist() {
        UpdateMovieRequest request = new UpdateMovieRequest(
                "Updated",
                "Description",
                120,
                AgeRating.PG,
                Genre.DRAMA,
                LocalDate.of(2026, 1, 1),
                null,
                null);

        when(movieRepository.findById(movieId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.updateMovie(movieId, request))
                .isInstanceOf(MovieNotFoundException.class);

        verify(movieRepository, never()).save(any());
    }

    @Test
    void discontinueMovie_shouldThrowMovieNotFoundException_whenMovieDoesNotExist() {
        when(movieRepository.findById(movieId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.discontinueMovie(movieId))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining(movieId.toString());

        verify(movieRepository).findById(movieId);
        verifyNoMoreInteractions(movieRepository);
    }

    @Test
    void restoreMovie_shouldThrowMovieNotFoundException_whenMovieDoesNotExist() {
        when(movieRepository.findById(movieId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> movieService.restoreMovie(movieId))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining(movieId.toString());

        verify(movieRepository).findById(movieId);
        verifyNoMoreInteractions(movieRepository);
    }
}
